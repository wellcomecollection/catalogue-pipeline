package uk.ac.wellcome.platform.transformer.mets.service

import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.bigmessaging.fixtures.{BigMessagingFixture, VHSFixture}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.store.HybridStoreEntry
import uk.ac.wellcome.storage.{Identified, Version}

import scala.concurrent.ExecutionContext.Implicits.global

class MetsTransformerWorkerServiceTest
  extends FunSpec
    with BigMessagingFixture
    with MetsGenerators
    with RandomStrings
    with Eventually
    with IntegrationPatience with VHSFixture[String] {

  it("retrieves a mets file from s3 and sends an invisible work") {

    val identifier = randomAlphanumeric(10)
    val version = randomInt(1, 10)
    val str = metsXmlWith(identifier, License_CCBYNC)

    withWorkerService { case (QueuePair(queue, _), topic, vhs) =>
      sendWork(str, "mets.xml", vhs, queue, version)
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


    withWorkerService { case (QueuePair(queue, dlq), topic, vhs) =>
      sendWork(value1, "mets.xml", vhs, queue, version)
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
      license = Some(License_CCBYNC))
    val expectedItem: MaybeDisplayable[Item] =
      Unidentifiable(Item(locations = List(expectedDigitalLocation)))


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
              identifierType =
                IdentifierType("sierra-system-number"),
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


  def withWorkerService[R](testWith: TestWith[(QueuePair, Topic, VHS), R]): R =
    withLocalSqsQueueAndDlq { case queuePair@QueuePair(queue, _) =>
      withLocalSnsTopic { topic =>
        withLocalS3Bucket { messagingBucket =>
          withLocalS3Bucket { storageBucket =>
            withVHS { vhs =>
              withActorSystem { implicit actorSystem =>
                withSQSStream[Version[String, Int], R](queue) { sqsStream =>
                  withSqsBigMessageSender[TransformedBaseWork, R](
                    messagingBucket,
                    topic,
                    snsClient) { messageSender =>
                    val workerService = new MetsTransformerWorkerService(
                      sqsStream,
                      messageSender, vhs)
                    workerService.run()
                    testWith((queuePair, topic, vhs))
                  }
                }
              }
            }
          }
        }
      }
    }

  private def sendWork(mets: String,
               name: String,
               vhs: VHS,
               queue: SQS.Queue,
               version: Int) = {
    val entry = HybridStoreEntry(mets, EmptyMetadata())
    val id = name
    val key = vhs.put(Version(id, version))(entry) match {
      case Left(err) => throw new Exception(s"Failed storing work in VHS: $err")
      case Right(Identified(key, _)) => key
    }
    sendSqsMessage(queue, key)
  }
}
