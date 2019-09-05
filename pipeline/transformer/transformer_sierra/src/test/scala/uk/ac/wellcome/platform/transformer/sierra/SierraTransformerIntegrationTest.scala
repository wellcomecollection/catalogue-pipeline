package uk.ac.wellcome.platform.transformer.sierra

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.platform.transformer.sierra.services.SierraTransformerWorkerService
import uk.ac.wellcome.platform.transformer.sierra.fixtures.HybridRecordReceiverFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}

import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.ObjectLocationPrefix
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

class SierraTransformerIntegrationTest
    extends FunSpec
    with Matchers
    with IntegrationPatience
    with DynamoFixtures
    with BigMessagingFixture
    with HybridRecordReceiverFixture
    with SierraGenerators {

  override def createTable(
    table: DynamoFixtures.Table): DynamoFixtures.Table = {
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )
  }

  it("transforms sierra records and publishes the result to the given topic") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withLocalS3Bucket { messagingBucket =>
            withLocalDynamoDbTable { table =>
              val vhs = VHSBuilder.build[SierraTransformable](
                ObjectLocationPrefix(
                  namespace = storageBucket.name,
                  path = "sierra"),
                DynamoConfig(table.name, table.index),
                dynamoClient,
                s3Client,
              )
              withWorkerService(vhs, topic, messagingBucket, queue) {
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
                      vhs
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
                      actualWork.title shouldBe title
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
  }

  def withWorkerService[R](vhs: VHS,
                           topic: Topic,
                           bucket: Bucket,
                           queue: Queue)(
    testWith: TestWith[SierraTransformerWorkerService[SNSConfig], R]): R =
    withHybridRecordReceiver(vhs, topic, bucket) { messageReceiver =>
      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          val workerService = new SierraTransformerWorkerService(
            messageReceiver = messageReceiver,
            sierraTransformer = new SierraTransformableTransformer,
            sqsStream = sqsStream
          )
          workerService.run()
          testWith(workerService)
        }
      }
    }
}
