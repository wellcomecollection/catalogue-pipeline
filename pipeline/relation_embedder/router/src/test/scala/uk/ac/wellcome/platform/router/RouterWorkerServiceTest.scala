package uk.ac.wellcome.platform.router

import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.CollectionPath
import uk.ac.wellcome.pipeline_storage.ElasticRetriever

class RouterWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with SQS
    with Akka
    with ElasticsearchFixtures
    with Eventually {

  it("sends collectionPath to paths topic") {
    val work = mergedWork().collectionPath(CollectionPath("a"))
    withActorSystem { implicit as =>
      implicit val es = as.dispatcher
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          val worksMessageSender = new MemoryMessageSender
          val pathsMessageSender = new MemoryMessageSender
          withSQSStream[NotificationMessage, Assertion](queue) { stream =>
            withLocalMergedWorksIndex { mergedIndex =>
              insertIntoElasticsearch(mergedIndex, work)
              sendNotificationToSQS(queue = queue, body = work.id)
              val service =
                new RouterWorkerService(
                  sqsStream = stream,
                  worksMsgSender = worksMessageSender,
                  pathsMsgSender = pathsMessageSender,
                  workRetriever =
                    new ElasticRetriever(elasticClient, mergedIndex))
              service.run()
              eventually {
                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
                pathsMessageSender.getMessages[CollectionPath]() should contain(
                  CollectionPath("a"))
                worksMessageSender.getMessages[String] shouldBe empty
              }
            }
          }
      }
    }
  }

  it("sends a work without collectionPath to works topic") {
    val work = mergedWork()
    withActorSystem { implicit as =>
      implicit val es = as.dispatcher
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          val worksMessageSender = new MemoryMessageSender
          val pathsMessageSender = new MemoryMessageSender
          withSQSStream[NotificationMessage, Assertion](queue) { stream =>
            withLocalMergedWorksIndex { mergedIndex =>
              insertIntoElasticsearch(mergedIndex, work)
              sendNotificationToSQS(queue = queue, body = work.id)
              val service =
                new RouterWorkerService(
                  sqsStream = stream,
                  worksMsgSender = worksMessageSender,
                  pathsMsgSender = pathsMessageSender,
                  workRetriever =
                    new ElasticRetriever(elasticClient, mergedIndex))
              service.run()
              eventually {
                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
                worksMessageSender.getMessages[String]() should contain(work.id)
                pathsMessageSender.getMessages[CollectionPath]() shouldBe empty
              }
            }
          }
      }
    }
  }

  it("sends on an invisible work") {
    val work = mergedWork().collectionPath(CollectionPath("a/2")).invisible()
    withActorSystem { implicit as =>
      implicit val es = as.dispatcher
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          val worksMessageSender = new MemoryMessageSender
          val pathsMessageSender = new MemoryMessageSender
          withSQSStream[NotificationMessage, Assertion](queue) { stream =>
            withLocalMergedWorksIndex { mergedIndex =>
              insertIntoElasticsearch(mergedIndex, work)
              sendNotificationToSQS(queue = queue, body = work.id)
              val service =
                new RouterWorkerService(
                  sqsStream = stream,
                  worksMsgSender = worksMessageSender,
                  pathsMsgSender = pathsMessageSender,
                  workRetriever =
                    new ElasticRetriever(elasticClient, mergedIndex))
              service.run()
              eventually {
                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
                worksMessageSender.getMessages[String]() shouldBe empty
                pathsMessageSender.getMessages[CollectionPath]() should contain(
                  CollectionPath("a/2"))
              }
            }
          }
      }
    }
  }

}
