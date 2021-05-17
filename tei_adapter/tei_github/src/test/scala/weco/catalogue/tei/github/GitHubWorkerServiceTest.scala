package weco.catalogue.tei.github

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import weco.catalogue.tei.github.fixtures.Wiremock

class GitHubWorkerServiceTest
    extends AnyFunSpec
    with SQS
    with Akka
    with Eventually
    with Wiremock
    with IntegrationPatience {
  it(
    "receives a window message calls the github api and sends a message with all files changed in that window") {
    val message = """ {
      "start": "2021-05-05T10:00:00Z",
      "end": "2021-05-07T18:00:00Z",
    }"""
    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue, createNotificationMessageWith(message))
        withActorSystem { implicit actorSystem =>
          implicit val ec = actorSystem.dispatcher
          withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
            val messageSender = new MemoryMessageSender()
            withWiremock("localhost") { port =>
              val service = new GitHubWorkerService(
                stream,
                new GitHubRetriever(s"http://localhost:$port", "master"),
                messageSender,
                10)
              service.run()
              eventually {
                messageSender
                  .getMessages[String]() should contain theSameElementsAs List(
                  "https://github.com/wellcomecollection/wellcome-collection-tei/raw/1e394d3186b6a8ed5f0fa8af33b99bdc59d7c544/Arabic/WMS_Arabic_49.xml",
                  "https://github.com/wellcomecollection/wellcome-collection-tei/raw/481fa2d65ec2b44a4293823aa86b6f5e455a7c0d/Arabic/WMS_Arabic_46.xml",
                  "https://github.com/wellcomecollection/wellcome-collection-tei/raw/db7581026bb9149330225dc2b9411202b3cd6894/Arabic/WMS_Arabic_62.xml"
                )
              }
            }
          }
        }
    }
  }
}
