package uk.ac.wellcome.platform.ingestor.common.services

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.funspec.AnyFunSpec
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.ac.wellcome.elasticsearch.{ElasticCredentials, ElasticsearchIndexCreator}
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.fixtures.SampleDocument
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorWorkerServiceTest
    extends AnyFunSpec
    with IngestorFixtures
    with IdentifiersGenerators
    with RandomGenerators {

  it("creates the index at startup if it doesn't already exist") {
    val index = createIndex
    withLocalSqsQueue() { queue =>
      withElasticIndexer[SampleDocument, Any](index) { indexer =>
        withWorkerService[SampleDocument, Any](
          queue,
          index,
          NoStrictMapping,
          indexer,
          elasticClient) { _ =>
          eventuallyIndexExists(index)
        }
      }
    }
  }

  it("ingests a single document") {
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric())
    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        sendMessage[SampleDocument](queue = queue, obj = document)
        val index = createIndex
        withElasticIndexer[SampleDocument, Any](index) { indexer =>
          withWorkerService[SampleDocument, Any](
            queue,
            index,
            NoStrictMapping,
            indexer,
            elasticClient) { _ =>
            assertElasticsearchEventuallyHas(index = index, document)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }

  }

  it("ingests lots of documents") {
    val documents = (1 to 250).map(_ =>
      SampleDocument(1, createCanonicalId, randomAlphanumeric()))
    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        documents.foreach(document =>
          sendMessage[SampleDocument](queue = queue, obj = document))
        val index = createIndex
        withElasticIndexer[SampleDocument, Any](index) { indexer =>
          withWorkerService[SampleDocument, Any](
            queue,
            index,
            NoStrictMapping,
            indexer,
            elasticClient) { _ =>
            assertElasticsearchEventuallyHas(index = index, documents: _*)
            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
        }
    }
  }

  it("does not delete a message from the queue if it fails processing") {
    withLocalSqsQueue() { queue =>
      val index = createIndex
      withElasticIndexer[SampleDocument, Any](index) { indexer =>
        withWorkerService[SampleDocument, Any](
          queue,
          index,
          NoStrictMapping,
          indexer,
          elasticClient) { _ =>
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
            getQueueAttribute(
              queue,
              attributeName =
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE) shouldBe "1"
          }
        }
      }
    }
  }

  it("when we cannot verify an index exists throw an exception") {
    val index = createIndex
    withLocalSqsQueue() { queue =>
      withActorSystem { implicit actorSystem =>
        withBigMessageStream[SampleDocument, Any](queue) { messageStream =>
          import scala.concurrent.duration._

          val brokenRestClient: RestClient = RestClient
            .builder(
              new HttpHost(
                "localhost",
                9800,
                "http"
              )
            )
            .setHttpClientConfigCallback(
              new ElasticCredentials("elastic", "changeme")
            )
            .build()

          val brokenClient: ElasticClient =
            ElasticClient(JavaClient.fromRestClient(brokenRestClient))

          val config = IngestorConfig(
            batchSize = 100,
            flushInterval = 5.seconds
          )
          withElasticIndexer[SampleDocument, Any](index, brokenClient) {
            indexer =>
              val service = new IngestorWorkerService(
                ingestorConfig = config,
                indexCreator = new ElasticsearchIndexCreator(
                  brokenClient,
                  index,
                  NoStrictMapping),
                messageStream = messageStream,
                documentIndexer = indexer
              )

              whenReady(service.run.failed) { e =>
                e shouldBe a[RuntimeException]
              }
          }
        }
      }

    }
  }

}
