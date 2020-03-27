package uk.ac.wellcome.platform.ingestor.common.services

import org.scalatest.FunSpec
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.ingestor.common.fixtures.{IngestorFixtures, SampleDocument}
import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorWorkerServiceTest extends FunSpec with IngestorFixtures with IdentifiersGenerators {
  it("ingests a single document"){
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric)
    withLocalSqsQueueAndDlqAndTimeout(visibilityTimeout = 10) { case QueuePair(queue, dlq) =>
      sendMessage[SampleDocument](queue = queue, obj = document)
      val index = createIndex
        withIndexer[SampleDocument, Any](index) { indexer =>
          withWorkerService[SampleDocument, Any](queue, index, NoStrictMapping, indexer, elasticClient) { _ =>

            assertElasticsearchEventuallyHas(index = index, document)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
      }

    }

  it("ingests multiple documents"){
    val documents = (1 to 250).map( _ => SampleDocument(1, createCanonicalId, randomAlphanumeric))
    withLocalSqsQueueAndDlqAndTimeout(visibilityTimeout = 10) { case QueuePair(queue, dlq) =>
      documents.foreach(document => sendMessage[SampleDocument](queue = queue, obj = document))
      val index = createIndex
      withIndexer[SampleDocument, Any](index) { indexer =>
        withWorkerService[SampleDocument, Any](queue, index, NoStrictMapping, indexer, elasticClient) { _ =>

          assertElasticsearchEventuallyHas(index = index, documents: _*)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
      }
    }
  }

  it("does not delete a message from the queue if it fails processing") {
    withLocalSqsQueue { queue =>
      val index = createIndex
      withIndexer[SampleDocument, Any](index) { indexer =>
        withWorkerService[SampleDocument, Any](queue, index, NoStrictMapping, indexer, elasticClient) { _ =>
          sendNotificationToSQS(
            queue = queue,
            body = "not a json string -- this will fail parsing"
          )

          // After a message is read, it stays invisible for 1 second and then it gets sent again.		               assertQueueHasSize(queue, size = 1)
          // So we wait for longer than the visibility timeout and then we assert that it has become
          // invisible again, which means that the ingestor picked it up again,
          // and so it wasn't deleted as part of the first run.
          // TODO Write this test using dead letter queues once https://github.com/adamw/elasticmq/issues/69 is closed
          Thread.sleep(2000)

          eventually {
            sqsClient
              .getQueueAttributes(
                queue.url,
                List("ApproximateNumberOfMessagesNotVisible").asJava
              )
              .getAttributes
              .get(
                "ApproximateNumberOfMessagesNotVisible"
              ) shouldBe "1"
          }
        }
      }
    }
  }


}
