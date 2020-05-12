package uk.ac.wellcome.platform.transformer.miro

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.miro.fixtures.MiroVHSRecordReceiverFixture
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.transformers.MiroTransformableWrapper
import uk.ac.wellcome.platform.transformer.miro.services.MiroTransformerWorkerService
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture

class MiroTransformerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with BigMessagingFixture
    with Eventually
    with IntegrationPatience
    with MiroRecordGenerators
    with MiroTransformableWrapper
    with MiroVHSRecordReceiverFixture {

  it("transforms miro records and publishes the result to the given topic") {
    val miroID = "M0000001"
    val title = "A guide for a giraffe"

    withLocalSnsTopic { topic =>
      withLocalSqsQueue() { queue =>
        withLocalS3Bucket { storageBucket =>
          withLocalS3Bucket { messageBucket =>
            val miroHybridRecordMessage =
              createHybridRecordNotificationWith(
                createMiroRecordWith(title = Some(title), imageNumber = miroID)
              )

            sendSqsMessage(
              queue = queue,
              obj = miroHybridRecordMessage
            )

            withWorkerService(topic, messageBucket, queue) { _ =>
              eventually {
                val works = getMessages[UnidentifiedWork](topic)
                works.length shouldBe >=(1)

                works.map { actualWork =>
                  actualWork.identifiers.head.value shouldBe miroID
                  actualWork.data.title shouldBe Some(title)
                }
              }
            }
          }
        }
      }
    }
  }

  // This is based on a specific bug that we found where different records
  // were written to the same s3 key because of the hashing algorithm clashing
  it("sends different messages for different miro records") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue() { queue =>
        withLocalS3Bucket { storageBucket =>
          val miroHybridRecordMessage1 =
            createHybridRecordNotificationWith(
              createMiroRecordWith(
                title = Some("Antonio Dionisi"),
                description = Some("Antonio Dionisi"),
                physFormat = Some("Book"),
                copyrightCleared = Some("Y"),
                imageNumber = "L0011975",
                useRestrictions = Some("CC-BY"),
                innopacID = Some("12917175"),
                creditLine = Some("Wellcome Library, London")
              )
            )
          val miroHybridRecordMessage2 =
            createHybridRecordNotificationWith(
              createMiroRecordWith(
                title = Some(
                  "Greenfield Sluder, Tonsillectomy..., use of guillotine"),
                description = Some("Use of the guillotine"),
                copyrightCleared = Some("Y"),
                imageNumber = "L0023034",
                useRestrictions = Some("CC-BY"),
                innopacID = Some("12074536"),
                creditLine = Some("Wellcome Library, London")
              )
            )

          withLocalS3Bucket { messageBucket =>
            withWorkerService(topic, messageBucket, queue) { _ =>
              sendSqsMessage(queue = queue, obj = miroHybridRecordMessage1)
              sendSqsMessage(queue = queue, obj = miroHybridRecordMessage2)

              eventually {
                val works = getMessages[UnidentifiedWork](topic)
                works.distinct.length shouldBe 2
              }
            }
          }
        }
      }
    }
  }

  def withWorkerService[R](topic: Topic, bucket: Bucket, queue: Queue)(
    testWith: TestWith[MiroTransformerWorkerService[SNSConfig], R]): R =
    withMiroVHSRecordReceiver(topic, bucket) { recordReceiver =>
      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          val workerService = new MiroTransformerWorkerService(
            vhsRecordReceiver = recordReceiver,
            miroTransformer = new MiroRecordTransformer,
            sqsStream = sqsStream
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }
}
