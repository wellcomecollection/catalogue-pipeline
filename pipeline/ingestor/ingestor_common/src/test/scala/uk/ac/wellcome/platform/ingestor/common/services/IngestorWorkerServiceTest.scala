package uk.ac.wellcome.platform.ingestor.common.services

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.ElasticCredentials
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
        withWorkerService[SampleDocument, Any](queue, index, indexer) { _ =>
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
          withWorkerService[SampleDocument, Any](queue, index, indexer) { _ =>
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
          withWorkerService[SampleDocument, Any](queue, index, indexer) { _ =>
            assertElasticsearchEventuallyHas(index = index, documents: _*)
            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
        }
    }
  }

  it("leaves a message on the queue if it fails processing") {
    val index = createIndex

    withLocalSqsQueuePair(visibilityTimeout = 1) { case QueuePair(queue, dlq) =>
      withElasticIndexer[SampleDocument, Any](index) { indexer =>
        withWorkerService[SampleDocument, Any](queue, index, indexer) { _ =>
          sendInvalidJSONto(queue)

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)
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
