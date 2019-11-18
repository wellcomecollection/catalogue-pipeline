package uk.ac.wellcome.platform.transformer.mets.service

import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal.{DigitalLocation, IdentifierType, Item, License_CCBYNC, LocationType, MaybeDisplayable, MergeCandidate, SourceIdentifier, TransformedBaseWork, Unidentifiable, UnidentifiedInvisibleWork, WorkData}
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3Config

import scala.concurrent.ExecutionContext.Implicits.global

class MetsTransformerWorkerServiceTest extends FunSpec with BigMessagingFixture with MetsGenerators with RandomStrings with Eventually with IntegrationPatience{

  it("retrieves a mets file from s3 and sends an invisible work"){
    withLocalSqsQueue{ queue =>
      withLocalSnsTopic { topic =>
        withLocalS3Bucket { messagingBucket =>
          withLocalS3Bucket { storageBucket =>
            val identifier = randomAlphanumeric(10)
            val version = randomInt(1, 10)
            val path = "mets.xml"
            s3Client.putObject(storageBucket.name,path,metsXmlWith(identifier, License_CCBYNC))

            sendSqsMessage(queue, MetsData(path, version))

            withWorkerService(topic = topic, messagingBucket = messagingBucket, storageBucket = storageBucket, queue = queue) { _ =>
              eventually{
                val works = getMessages[UnidentifiedInvisibleWork](topic)
                works.length should be >= 1

                val url = s"https://wellcomelibrary.org/iiif/$identifier/manifest"
                val digitalLocation = DigitalLocation(url,LocationType("iiif-presentation"),license = Some(License_CCBYNC))
                val item: MaybeDisplayable[Item] = Unidentifiable(Item(locations = List(digitalLocation)))
                works.head shouldBe UnidentifiedInvisibleWork(
                  version = version,
                  sourceIdentifier = SourceIdentifier(identifierType = IdentifierType("mets", "METS"), ontologyType = "Work", value = identifier),
                  data = WorkData(items = List(item),
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

              }
            }
          }
        }
      }
    }
  }

  def withWorkerService[R](topic: Topic,
                           messagingBucket: Bucket,
                           storageBucket: Bucket,
                           queue: Queue)(
                            testWith: TestWith[MetsTransformerWorkerService, R]): R =
      withActorSystem { implicit actorSystem =>
        withSQSStream[MetsData, R](queue) { sqsStream =>
          withSqsBigMessageSender[TransformedBaseWork, R](messagingBucket, topic, snsClient) { messageSender =>
            val workerService = new MetsTransformerWorkerService(sqsStream, messageSender, s3Client, S3Config(storageBucket.name))
            workerService.run()
            testWith(workerService)
          }
        }
      }
}
