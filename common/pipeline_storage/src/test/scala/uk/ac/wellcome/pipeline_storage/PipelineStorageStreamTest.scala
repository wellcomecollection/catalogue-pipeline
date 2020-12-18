package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.{ElasticClientBuilder, NoStrictMapping}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  PipelineStorageStreamFixtures,
  SampleDocument
}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}

class PipelineStorageStreamTest
    extends AnyFunSpec
    with ElasticIndexerFixtures
    with IdentifiersGenerators
    with ElasticsearchFixtures
    with PipelineStorageStreamFixtures {

  def indexer(index: Index, elasticClient: ElasticClient = elasticClient) =
    new ElasticIndexer[SampleDocument](elasticClient, index, NoStrictMapping)

  it("creates the index at startup if it doesn't already exist") {
    val index = createIndex
    val response: Response[IndexExistsResponse] =
      elasticClient
        .execute(indexExists(index.name))
        .await
    response.result.isExists shouldBe false
    withLocalSqsQueue() { queue =>
      withPipelineStream(queue = queue, indexer = indexer(index)) {
        pipelineStream =>
          pipelineStream.foreach("test_stream", _ => Future.successful(None))
          eventuallyIndexExists(index)
      }
    }
  }

  it("ingests a single document and sends it on") {
    val index = createIndex
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric())

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = sender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = document
          )
          pipelineStream.foreach(
            "test stream",
            message =>
              Future
                .fromTry(JsonUtil.fromJson[SampleDocument](message.body))
                .map(Option(_)))
          assertElasticsearchEventuallyHas(index = index, document)
          eventually {
            sender.messages.map(_.body) should contain(document.canonicalId)
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("processes a message and does not ingest if result of process is a None") {
    val index = createIndex
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric())

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = sender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = document
          )
          pipelineStream.foreach("test stream", _ => Future.successful(None))

          eventually {
            assertElasticsearchEmpty(index = index)
            sender.messages.map(_.body) shouldBe empty
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("ingests lots of documents") {
    val index = createIndex
    val documents = (1 to 250).map(_ =>
      SampleDocument(1, createCanonicalId, randomAlphanumeric()))

    val pipelineStorageConfig = PipelineStorageConfig(
      batchSize = 150,
      flushInterval = 10 seconds,
      parallelism = 10
    )

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 30) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = sender,
          pipelineStorageConfig = pipelineStorageConfig
        ) { pipelineStream =>
          documents.foreach(
            doc =>
              sendNotificationToSQS(
                queue = queue,
                message = doc
            ))
          pipelineStream.foreach(
            "test stream",
            message =>
              Future
                .fromTry(JsonUtil.fromJson[SampleDocument](message.body))
                .map(Option(_)))
          assertElasticsearchEventuallyHas(index = index, documents: _*)
          eventually(Timeout(scaled(60 seconds))) {
            sender.messages.map(_.body) should contain theSameElementsAs documents
              .map(_.canonicalId)
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("leaves a message on the queue if it fails processing") {
    val index = createIndex
    val sender = new MemoryMessageSender

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withPipelineStream(queue = queue, indexer = indexer(index)) {
          pipelineStream =>
            sendInvalidJSONto(queue)
            pipelineStream.foreach(
              "test stream",
              message =>
                Future
                  .fromTry(JsonUtil.fromJson[SampleDocument](message.body))
                  .map(Option(_)))
            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)
              sender.messages shouldBe empty
            }
        }
    }
  }

  it("when we cannot verify an index exists throw an exception") {
    val index = createIndex
    val brokenClient = ElasticClientBuilder.create(
      hostname = "localhost",
      port = 9800,
      protocol = "http",
      username = "elastic",
      password = "dontletmein"
    )

    withLocalSqsQueue() { queue =>
      withPipelineStream(queue = queue, indexer = indexer(index, brokenClient)) {
        pipelineStream =>
          whenReady(
            pipelineStream
              .foreach("test stream", _ => Future.successful(None))
              .failed) { exception =>
            exception shouldBe a[RuntimeException]
          }
      }
    }
  }

  it("does not delete from the queue documents that failed indexing") {
    val successfulDocuments = (1 to 5).map { _ =>
      SampleDocument(
        version = 1,
        canonicalId = createCanonicalId,
        title = randomAlphanumeric())
    }
    val failingDocuments = (1 to 5).map { _ =>
      val canonicalId = createCanonicalId
      val doc = SampleDocument(
        version = 1,
        canonicalId = canonicalId,
        title = randomAlphanumeric())
      (canonicalId, doc)
    }.toMap
    val documents = successfulDocuments ++ failingDocuments.values
    val indexer = new Indexer[SampleDocument] {
      override def init(): Future[Unit] = Future.successful(())

      override def apply(documents: Seq[SampleDocument])
        : Future[Either[Seq[SampleDocument], Seq[SampleDocument]]] = {
        Future.successful(Left(documents.filter(d =>
          failingDocuments.keySet.contains(d.canonicalId))))
      }
    }

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withPipelineStream(queue = queue, indexer = indexer, sender = sender) {
          pipelineStream =>
            documents.foreach(
              doc =>
                sendNotificationToSQS(
                  queue = queue,
                  message = doc
              ))
            pipelineStream.foreach(
              "test stream",
              message =>
                Future
                  .fromTry(JsonUtil.fromJson[SampleDocument](message.body))
                  .map(Option(_)))
            eventually {
              sender.messages.map(_.body) should contain theSameElementsAs successfulDocuments
                .map(_.canonicalId)
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 5)
            }
        }
    }
  }

  it("leaves a document on the queue if it can't send an onward message") {
    val document = SampleDocument(
      version = 1,
      canonicalId = createCanonicalId,
      title = randomAlphanumeric()
    )

    val brokenSender = new MemoryMessageSender() {
      override def send(body: String): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(createIndex),
          sender = brokenSender) { pipelineStream =>
          sendNotificationToSQS(queue, document)

          pipelineStream.foreach(
            "test stream",
            _ => Future.successful(Some(document))
          )

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)
          }
        }
    }
  }
}
