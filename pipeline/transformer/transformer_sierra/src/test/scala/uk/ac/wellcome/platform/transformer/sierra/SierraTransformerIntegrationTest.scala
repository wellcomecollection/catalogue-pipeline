package uk.ac.wellcome.platform.transformer.sierra

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.platform.transformer.sierra.services.{
  HybridRecord,
  SierraTransformerWorkerService
}
import uk.ac.wellcome.platform.transformer.sierra.fixtures.BackwardsCompatHybridRecordReceiverFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.platform.transformer.sierra.model.SierraTransformableImplicits._

class SierraTransformerIntegrationTest
    extends AnyFunSpec
    with Matchers
    with IntegrationPatience
    with BigMessagingFixture
    with BackwardsCompatHybridRecordReceiverFixture
    with SierraGenerators
    with IdentifiersGenerators {

  it("transforms sierra records and publishes the result to the given topic") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withLocalS3Bucket { messagingBucket =>
            val store = S3TypedStore[SierraTransformable]
            withWorkerService(store, topic, messagingBucket, queue) {
              workerService =>
                val id = createSierraBibNumber
                val title = "A pot of possums"
                val sierraTransformable = SierraTransformable(
                  bibRecord = createSierraBibRecordWith(
                    id = id,
                    data = s"""
                   |{
                   | "id": "$id",
                   | "title": "$title",
                   | "varFields": []
                   |}
                    """.stripMargin
                  )
                )
                sendSqsMessage(
                  queue = queue,
                  obj = createHybridRecordNotificationWith(
                    sierraTransformable,
                    store,
                    namespace = storageBucket.name,
                  )
                )
                eventually {
                  val snsMessages = listMessagesReceivedFromSNS(topic)
                  snsMessages.size should be >= 1

                  val sourceIdentifier =
                    createSierraSystemSourceIdentifierWith(
                      value = id.withCheckDigit
                    )

                  val sierraIdentifier =
                    createSierraIdentifierSourceIdentifierWith(
                      value = id.withoutCheckDigit
                    )

                  val works = getMessages[UnidentifiedWork](topic)
                  works.length shouldBe >=(1)

                  works.map { actualWork =>
                    actualWork.sourceIdentifier shouldBe sourceIdentifier
                    actualWork.data.title shouldBe Some(title)
                    actualWork.identifiers shouldBe List(
                      sourceIdentifier,
                      sierraIdentifier)
                  }
                }
            }
          }
        }
      }
    }
  }

  def withWorkerService[R](store: SierraStore,
                           topic: Topic,
                           bucket: Bucket,
                           queue: Queue)(
    testWith: TestWith[SierraTransformerWorkerService[SNSConfig, HybridRecord],
                       R]): R =
    withHybridRecordReceiver(store, topic, bucket) { messageReceiver =>
      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          val workerService = new SierraTransformerWorkerService(
            messageReceiver = messageReceiver,
            sqsStream = sqsStream
          )
          workerService.run()
          testWith(workerService)
        }
      }
    }
}
