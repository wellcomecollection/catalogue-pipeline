package uk.ac.wellcome.platform.transformer.mets.service

import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.AmazonS3
import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.mets.client.ClientFactory
import uk.ac.wellcome.platform.transformer.mets.fixtures.{
  LocalStackS3Fixtures,
  MetsGenerators,
  STSFixtures
}
import uk.ac.wellcome.platform.transformer.mets.store.TemporaryCredentialsStore
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{TypedStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}

class MetsTransformerWorkerServiceTest
    extends AnyFunSpec
    with BigMessagingFixture
    with MetsGenerators
    with RandomStrings
    with Eventually
    with IntegrationPatience
    with STSFixtures
    with LocalStackS3Fixtures {

  val roleArn = "arn:aws:iam::123456789012:role/new_role"

  // The test S3 container requires a specific accessKey and secretKey so
  // it fails if we use the temporary credentials
  object BypassCredentialsClientFactory extends ClientFactory[AmazonS3] {
    override def buildClient(credentials: BasicSessionCredentials): AmazonS3 =
      s3Client
  }

  it("retrieves a mets file from s3 and sends an invisible work") {

    val identifier = randomAlphanumeric(10)
    val version = randomInt(1, 10)
    val str = metsXmlWith(identifier, Some(License.CCBYNC))

    withWorkerService {
      case (QueuePair(queue, _), metsBucket, topic, dynamoStore) =>
        sendWork(str, "mets.xml", dynamoStore, metsBucket, queue, version)
        eventually {
          val works = getMessages[UnidentifiedInvisibleWork](topic)
          works.length should be >= 1

          assertQueueEmpty(queue)

          works.head shouldBe expectedWork(identifier, version)
        }
    }
  }

  it("sends failed messages to the dlq") {

    val version = randomInt(1, 10)
    val value1 = "this is not a valid mets file"

    withWorkerService {
      case (QueuePair(queue, dlq), metsBucket, topic, vhs) =>
        sendWork(value1, "mets.xml", vhs, metsBucket, queue, version)
        eventually {
          val works = getMessages[UnidentifiedInvisibleWork](topic)
          works should have size 0

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
        }
    }
  }

  private def expectedWork(identifier: String, version: Int) = {
    val expectedUrl =
      s"https://wellcomelibrary.org/iiif/$identifier/manifest"
    val expectedDigitalLocation = DigitalLocation(
      expectedUrl,
      LocationType("iiif-presentation"),
      license = Some(License.CCBYNC))
    val expectedItem =
      Item(id = Unidentifiable, locations = List(expectedDigitalLocation))

    val expectedWork = UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType("mets", "METS"),
        ontologyType = "Work",
        value = identifier),
      data = WorkData(
        items = List(expectedItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = identifier
            ),
            reason = Some("METS work")
          )
        )
      )
    )
    expectedWork
  }

  def withWorkerService[R](
    testWith: TestWith[(QueuePair,
                        Bucket,
                        Topic,
                        VersionedStore[String, Int, MetsLocation]),
                       R]): R =
    withLocalSqsQueueAndDlq {
      case queuePair @ QueuePair(queue, _) =>
        withLocalSnsTopic { topic =>
          withLocalS3Bucket { messagingBucket =>
            withLocalS3Bucket { metsBucket =>
              withMemoryStore { versionedStore =>
                withActorSystem { implicit actorSystem =>
                  withSQSStream[NotificationMessage, R](queue) { sqsStream =>
                    withSqsBigMessageSender[TransformedBaseWork, R](
                      messagingBucket,
                      topic,
                      snsClient) { messageSender =>
                      withAssumeRoleClientProvider(roleArn)(
                        BypassCredentialsClientFactory) {
                        assumeRoleclientProvider =>
                          val workerService = new MetsTransformerWorkerService(
                            sqsStream,
                            messageSender,
                            versionedStore,
                            new TemporaryCredentialsStore[String](
                              assumeRoleclientProvider)
                          )
                          workerService.run()
                          testWith(
                            (queuePair, metsBucket, topic, versionedStore))
                      }
                    }
                  }
                }
              }
            }
          }
        }
    }

  def withMemoryStore[R](
    testWith: TestWith[VersionedStore[String, Int, MetsLocation], R]): R = {
    testWith(MemoryVersionedStore(Map()))
  }

  private def sendWork(mets: String,
                       name: String,
                       dynamoStore: VersionedStore[String, Int, MetsLocation],
                       metsBucket: Bucket,
                       queue: SQS.Queue,
                       version: Int) = {
    val rootPath = "data"
    val key = for {
      _ <- S3TypedStore[String].put(
        ObjectLocation(metsBucket.name, s"$rootPath/$name"))(
        TypedStoreEntry(mets, Map()))
      entry = MetsLocation(metsBucket.name, rootPath, 1, name, List())
      key <- dynamoStore.put(Version(name, version))(entry)
    } yield key

    key match {
      case Left(err)                 => throw new Exception(s"Failed storing work in VHS: $err")
      case Right(Identified(key, _)) => sendNotificationToSQS(queue, key)
    }

  }
}
