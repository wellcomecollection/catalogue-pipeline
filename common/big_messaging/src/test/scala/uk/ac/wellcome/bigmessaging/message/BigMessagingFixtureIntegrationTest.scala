package uk.ac.wellcome.bigmessaging.message

import java.util.concurrent.ConcurrentLinkedDeque
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

import org.scalatest.Assertion
import uk.ac.wellcome.messaging.sns.SNSConfig
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sns.model.{
  SubscribeRequest,
  SubscribeResponse,
  UnsubscribeRequest
}
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import software.amazon.awssdk.services.sqs.model._

import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.{fixture, Fixture, TestWith}
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.messaging.sqs.SQSClientFactory
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.memory.MemoryMetrics

import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

class BigMessagingFixtureIntegrationTest
    extends AnyFunSpec
    with Matchers
    with BigMessagingFixture
    with Eventually
    with IntegrationPatience {

  val sqsAccessKey = "access"
  val sqsSecretKey = "secret"

  def localStackEndpoint(queue: Queue) =
    s"sqs://${queue.name}"

  val localStackSqsClient: SqsClient = SQSClientFactory.createSyncClient(
    region = "localhost",
    endpoint = "http://localhost:4576",
    accessKey = sqsAccessKey,
    secretKey = sqsSecretKey
  )

  val localStackSqsAsyncClient: SqsAsyncClient =
    SQSClientFactory.createAsyncClient(
      region = "localhost",
      endpoint = "http://localhost:4576",
      accessKey = sqsAccessKey,
      secretKey = sqsSecretKey
    )

  def createMessage(size: Int) = ExampleObject("a" * size)

  val smallMessage: ExampleObject = createMessage(size = 100)
  val largeMessage: ExampleObject = createMessage(size = 300000)

  val subject = "message-integration-test-subject"

  it("sends and receives a message <256KB") {
    assertMessagesCanBeSentAndReceived(List(smallMessage))
  }

  it("sends and receives a message >256KB") {
    assertMessagesCanBeSentAndReceived(List(largeMessage))
  }

  it("sends and receives messages with a mixture of sizes") {
    val sizes = List(10, 50, 100, 280000, 20, 290000)
    assertMessagesCanBeSentAndReceived(
      sizes.map { createMessage }
    )
  }

  private def assertMessagesCanBeSentAndReceived(
    messages: List[ExampleObject]): Assertion =
    withLocalStackBigMessageSenderMessageStream {
      case (messageStream, messageSender) =>
        val receivedMessages = new ConcurrentLinkedDeque[ExampleObject]()

        messages.map { msg =>
          messageSender.sendT(msg)
        }

        messageStream.foreach(
          "integration-test-stream",
          obj => Future { receivedMessages.push(obj) })
        eventually {
          receivedMessages should contain theSameElementsAs messages
        }
    }

  def withLocalStackSubscription[R](
    queue: Queue,
    topic: Topic): Fixture[SubscribeResponse, R] =
    fixture[SubscribeResponse, R](
      create = {
        val subRequest = SubscribeRequest
          .builder()
          .topicArn(topic.arn)
          .protocol("sqs")
          .endpoint(queue.arn)
          .build()
        info(s"Subscribing queue ${queue.arn} to topic ${topic.arn}")

        localStackSnsClient.subscribe(subRequest)
      },
      destroy = { subscribeResult =>
        val unsubscribeRequest =
          UnsubscribeRequest.builder
            .subscriptionArn(subscribeResult.subscriptionArn())
            .build()
        localStackSnsClient.unsubscribe(unsubscribeRequest)
      }
    )

  private def withLocalStackBigMessageSenderMessageStream[R](
    testWith: TestWith[(BigMessageStream[ExampleObject],
                        BigMessageSender[SNSConfig]),
                       R]): R = {
    withLocalStackMessageStreamFixtures[R] {
      case (queue, messageStream, store) =>
        withLocalS3Bucket { bucket =>
          withLocalStackSnsTopic { topic =>
            withLocalStackSubscription(queue, topic) { _ =>
              withSqsBigMessageSender(
                bucket,
                topic,
                localStackSnsClient) { messageWriter =>
                testWith((messageStream, messageWriter))
              }
            }
          }
        }
    }
  }

  def withLocalStackMessageStreamFixtures[R](
    testWith: TestWith[(Queue,
                        BigMessageStream[ExampleObject],
                        Store[ObjectLocation, ExampleObject]),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      val metrics = new MemoryMetrics[StandardUnit]()
      implicit val storeT: Store[ObjectLocation, ExampleObject] =
        new MemoryStore(Map.empty)

      withLocalStackSqsQueue() { queue =>
        withBigMessageStream[ExampleObject, R](
          queue,
          metrics,
          localStackSqsAsyncClient) { messageStream =>
          testWith((queue, messageStream, storeT))
        }
      }
    }

  def withLocalStackSqsQueue[R](
    queueName: String = Random.alphanumeric take 10 mkString,
  ): Fixture[Queue, R] =
    fixture[Queue, R](
      create = {
        val response = localStackSqsClient.createQueue {
          builder: CreateQueueRequest.Builder =>
            builder.queueName(queueName)
        }

        val arn = localStackSqsClient
          .getQueueAttributes { builder =>
            builder
              .queueUrl(response.queueUrl())
              .attributeNames(QueueAttributeName.QUEUE_ARN)
          }
          .attributes()
          .get(QueueAttributeName.QUEUE_ARN)

        val queue = Queue(
          url = response.queueUrl(),
          arn = arn,
          visibilityTimeout = 1
        )

        queue
      },
      destroy = { queue =>
        localStackSqsClient.purgeQueue { builder: PurgeQueueRequest.Builder =>
          builder.queueUrl(queue.url)
        }
        localStackSqsClient.deleteQueue { builder: DeleteQueueRequest.Builder =>
          builder.queueUrl(queue.url)
        }
      }
    )
}
