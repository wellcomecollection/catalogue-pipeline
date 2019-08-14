package uk.ac.wellcome.bigmessaging.message

import java.util.concurrent.ConcurrentLinkedDeque

import com.amazonaws.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.messaging.sns.{SNSConfig}
import com.amazonaws.services.sns.model.{
  SubscribeRequest,
  SubscribeResult,
  UnsubscribeRequest
}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.{fixture, Fixture, TestWith}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import uk.ac.wellcome.storage.streaming.Codec._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessagingFixtureIntegrationTest
    extends FunSpec
    with Matchers
    with BigMessagingFixture
    with Eventually
    with IntegrationPatience {

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

  def withLocalStackSubscription[R](queue: Queue,
                                    topic: Topic): Fixture[SubscribeResult, R] =
    fixture[SubscribeResult, R](
      create = {
        val subRequest = new SubscribeRequest(topic.arn, "sqs", queue.arn)
        info(s"Subscribing queue ${queue.arn} to topic ${topic.arn}")

        localStackSnsClient.subscribe(subRequest)
      },
      destroy = { subscribeResult =>
        val unsubscribeRequest =
          new UnsubscribeRequest(subscribeResult.getSubscriptionArn)
        localStackSnsClient.unsubscribe(unsubscribeRequest)
      }
    )

  private def withLocalStackBigMessageSenderMessageStream[R](
    testWith: TestWith[(MessageStream[ExampleObject],
                        BigMessageSender[SNSConfig, ExampleObject]),
                       R]): R = {
    withLocalStackMessageStreamFixtures[R] {
      case (queue, messageStream, store) =>
        withLocalS3Bucket { bucket =>
          withLocalStackSnsTopic { topic =>
            withLocalStackSubscription(queue, topic) { _ =>
              withSqsBigMessageSender(
                bucket,
                topic,
                localStackSnsClient,
                Some(store)) { messageWriter =>
                testWith((messageStream, messageWriter))
              }
            }
          }
        }
    }
  }

  def withLocalStackMessageStreamFixtures[R](
    testWith: TestWith[(Queue,
                        MessageStream[ExampleObject],
                        MemoryTypedStore[ObjectLocation, ExampleObject]),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      val metrics = new MemoryMetrics[StandardUnit]()
      implicit val typedStoreT =
        MemoryTypedStoreCompanion[ObjectLocation, ExampleObject]()

      withLocalStackSqsQueue { queue =>
        withMessageStream[ExampleObject, R](queue, metrics) { messageStream =>
          testWith((queue, messageStream, typedStoreT))
        }
      }
    }
}
