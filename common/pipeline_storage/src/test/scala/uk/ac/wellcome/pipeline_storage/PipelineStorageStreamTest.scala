package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.{ElasticClientBuilder, NoStrictMapping}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.{RandomGenerators, TestWith}
import uk.ac.wellcome.json.JsonUtil
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  SampleDocument
}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}

class PipelineStorageStreamTest
    extends AnyFunSpec
    with ElasticIndexerFixtures
    with IdentifiersGenerators
    with RandomGenerators
    with ElasticsearchFixtures
    with SQS
    with Akka {

  val pipelineStorageConfig = PipelineStorageConfig(
    batchSize = 1,
    flushInterval = 1 seconds,
    parallelism = 10)

  def indexer(index: Index, elasticClient: ElasticClient = elasticClient) =
    new ElasticIndexer[SampleDocument](elasticClient, index, NoStrictMapping)

  def withPipelineStream[R](
    indexer: Indexer[SampleDocument] = new MemoryIndexer[SampleDocument](
      index = mutable.Map[String, SampleDocument]()
    ),
    sender: MemoryMessageSender = new MemoryMessageSender(),
    visibilityTimeout: Int = 1,
    pipelineStorageConfig: PipelineStorageConfig = pipelineStorageConfig)(
    testWith: TestWith[
      (PipelineStorageStream[NotificationMessage, SampleDocument, String],
       QueuePair,
       MemoryMessageSender),
      R]): R =
    withActorSystem { implicit ac =>
      withLocalSqsQueuePair(visibilityTimeout) {
        case q @ QueuePair(queue, _) =>
          withSQSStream[NotificationMessage, R](queue) { stream =>
            testWith(
              (
                new PipelineStorageStream(stream, indexer, sender)(
                  pipelineStorageConfig),
                q,
                sender))
          }

      }
    }

  it("creates the index at startup if it doesn't already exist") {
    val index = createIndex
    val response: Response[IndexExistsResponse] =
      elasticClient
        .execute(indexExists(index.name))
        .await
    response.result.isExists shouldBe false
    withPipelineStream(indexer(index)) {
      case (pipelineStream, _, _) =>
        pipelineStream.foreach("test_stream", _ => Future.successful(None))
        eventuallyIndexExists(index)
    }
  }

  it("ingests a single document and sends it on") {
    val index = createIndex
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric())
    withPipelineStream(indexer(index), visibilityTimeout = 10) {
      case (pipelineStream, QueuePair(queue, dlq), messageSender) =>
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
          messageSender.messages.map(_.body) should contain(
            document.canonicalId)
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }

  }

  it("processes a message and does not ingest if result of process is a None") {
    val index = createIndex
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric())
    withPipelineStream(indexer(index), visibilityTimeout = 10) {
      case (pipelineStream, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(
          queue = queue,
          message = document
        )
        pipelineStream.foreach("test stream", _ => Future.successful(None))

        eventually {
          assertElasticsearchEmpty(index = index)
          messageSender.messages.map(_.body) shouldBe empty
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }

  }

  it("ingests lots of documents") {
    val index = createIndex
    val documents = (1 to 250).map(_ =>
      SampleDocument(1, createCanonicalId, randomAlphanumeric()))
    withPipelineStream(
      indexer(index),
      visibilityTimeout = 30,
      pipelineStorageConfig = PipelineStorageConfig(
        batchSize = 150,
        flushInterval = 10 seconds,
        parallelism = 10)) {
      case (pipelineStream, QueuePair(queue, dlq), messageSender) =>
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
          messageSender.messages.map(_.body) should contain theSameElementsAs (documents
            .map(_.canonicalId))
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("leaves a message on the queue if it fails processing") {
    val index = createIndex
    withPipelineStream(indexer(index)) {
      case (pipelineStream, QueuePair(queue, dlq), messageSender) =>
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
          messageSender.messages shouldBe empty
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
    withPipelineStream(indexer(index, brokenClient)) {
      case (pipelineStream, _, _) =>
        whenReady(
          pipelineStream
            .foreach("test stream", _ => Future.successful(None))
            .failed) { exception =>
          exception shouldBe a[RuntimeException]
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

      override def index(documents: Seq[SampleDocument])
        : Future[Either[Seq[SampleDocument], Seq[SampleDocument]]] = {
        Future.successful(Left(documents.filter(d =>
          failingDocuments.keySet.contains(d.canonicalId))))
      }
    }
    withPipelineStream(indexer) {
      case (pipelineStream, QueuePair(queue, dlq), messageSender) =>
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
          messageSender.messages.map(_.body) should contain theSameElementsAs (successfulDocuments
            .map(_.canonicalId))
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 5)
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

    withPipelineStream(sender = brokenSender) {
      case (pipelineStream, QueuePair(queue, dlq), _) =>
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
