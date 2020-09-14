package uk.ac.wellcome.platform.transformer.mets.service

import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.AmazonS3
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import software.amazon.awssdk.services.sqs.model.SendMessageResponse

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.models.Implicits._
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
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import WorkState.Unidentified

class MetsTransformerWorkerServiceTest
    extends AnyFunSpec
    with MetsGenerators
    with RandomStrings
    with Eventually
    with IntegrationPatience
    with STSFixtures
    with LocalStackS3Fixtures
    with SQS
    with Akka {

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
      case (QueuePair(queue, _), metsBucket, messageSender, dynamoStore) =>
        sendWork(str, "mets.xml", dynamoStore, metsBucket, queue, version)
        eventually {
          val works = messageSender.getMessages[Work.Invisible[Unidentified]]
          works.head shouldBe expectedWork(identifier, version)

          assertQueueEmpty(queue)
        }
    }
  }

  it("sends failed messages to the dlq") {

    val version = randomInt(1, 10)
    val value1 = "this is not a valid mets file"

    withWorkerService {
      case (QueuePair(queue, dlq), metsBucket, messageSender, vhs) =>
        sendWork(value1, "mets.xml", vhs, metsBucket, queue, version)
        eventually {
          messageSender.getMessages[Work.Invisible[Unidentified]] shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("sends messages that can be decoded as a Work[Unidentified]") {

    val identifier = randomAlphanumeric(10)
    val version = randomInt(1, 10)
    val str = metsXmlWith(identifier, Some(License.CCBYNC))

    withWorkerService {
      case (QueuePair(queue, _), metsBucket, messageSender, dynamoStore) =>
        sendWork(str, "mets.xml", dynamoStore, metsBucket, queue, version)
        eventually {
          val works = messageSender.getMessages[Work[Unidentified]]
          works.head shouldBe expectedWork(identifier, version)
        }
    }
  }

  private def expectedWork(identifier: String, version: Int): InvisibleWork = {
    val expectedUrl =
      s"https://wellcomelibrary.org/iiif/$identifier/manifest"
    val expectedDigitalLocation = DigitalLocationDeprecated(
      expectedUrl,
      LocationType("iiif-presentation"),
      license = Some(License.CCBYNC))
    val expectedItem =
      Item(
        id = IdState.Unidentifiable,
        locations = List(expectedDigitalLocation))

    val expectedWork = Work.Invisible[Unidentified](
      version = version,
      state = Unidentified(
        SourceIdentifier(
          identifierType = IdentifierType("mets", "METS"),
          ontologyType = "Work",
          value = identifier
        )
      ),
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
                        MemoryMessageSender,
                        VersionedStore[String, Int, MetsLocation]),
                       R]): R =
    withLocalSqsQueuePair() {
      case queuePair @ QueuePair(queue, _) =>
        val messageSender = new MemoryMessageSender()

        withLocalS3Bucket { metsBucket =>
          withMemoryStore { versionedStore =>
            withActorSystem { implicit actorSystem =>
              withSQSStream[NotificationMessage, R](queue) { sqsStream =>
                withAssumeRoleClientProvider(roleArn)(
                  BypassCredentialsClientFactory) { assumeRoleclientProvider =>
                  val workerService = new MetsTransformerWorkerService(
                    sqsStream,
                    messageSender,
                    versionedStore,
                    new TemporaryCredentialsStore[String](
                      assumeRoleclientProvider)
                  )

                  workerService.run()

                  testWith(
                    (queuePair, metsBucket, messageSender, versionedStore))
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
                       version: Int): SendMessageResponse = {
    val rootPath = "data"
    val key = for {
      _ <- S3TypedStore[String].put(
        S3ObjectLocation(metsBucket.name, key = s"$rootPath/$name"))(mets)
      entry = MetsLocation(metsBucket.name, rootPath, 1, name, List())
      key <- dynamoStore.put(Version(name, version))(entry)
    } yield key

    key match {
      case Left(err)                 => throw new Exception(s"Failed storing work in VHS: $err")
      case Right(Identified(key, _)) => sendNotificationToSQS(queue, key)
    }
  }
}
