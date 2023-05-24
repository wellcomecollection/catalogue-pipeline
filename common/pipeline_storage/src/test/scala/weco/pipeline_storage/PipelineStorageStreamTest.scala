package weco.pipeline_storage

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.index.IndexFixturesOld
import weco.elasticsearch.{ElasticClientBuilder, IndexConfig}
import weco.json.JsonUtil
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentGenerators
}
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Try}

class PipelineStorageStreamTest
    extends AnyFunSpec
    with IndexFixturesOld
    with PipelineStorageStreamFixtures
    with SampleDocumentGenerators {

  def indexer(index: Index, elasticClient: ElasticClient = elasticClient) =
    new ElasticIndexer[SampleDocument](elasticClient, index, IndexConfig.empty)

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
          pipelineStream.foreach("test_stream", _ => Future.successful(Nil))
          eventuallyIndexExists(index)
      }
    }
  }

  it("ingests a single document and sends it on") {
    val index = createIndex
    val document = createDocument

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 10.seconds) {
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
                .map(List(_)))
          assertElasticsearchEventuallyHas(index = index, document)
          eventually {
            sender.messages.map(_.body) should contain(document.id)
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("supports multiple documents for a single input") {
    val index = createIndex
    val documentsFirstMessage = (1 to 2)
      .map(_ => createDocument)
      .toList
    val documentsSecondMessage = (1 to 3)
      .map(_ => createDocument)
      .toList
    val documents = documentsFirstMessage ++ documentsSecondMessage

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 10.seconds) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = sender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = 1
          )
          sendNotificationToSQS(
            queue = queue,
            message = 2
          )
          pipelineStream.foreach("test stream", msg => {
            if (Integer.parseInt(msg.body) == 1)
              Future.successful(documentsFirstMessage)
            else Future.successful(documentsSecondMessage)
          })
          assertElasticsearchEventuallyHas(index = index, documents: _*)
          eventually {
            sender.messages.map(_.body) should contain theSameElementsAs documents
              .map(_.id)
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("does not delete a message if some of the process results fail ingesting") {
    val documentsFailingMessage = (1 to 2)
      .map(_ => createDocument)
      .toList
    val documentsSuccessfulMessage = (1 to 3)
      .map(_ => createDocument)
      .toList

    val failingDocument = documentsFailingMessage.head
    val indexer = new Indexer[SampleDocument] {
      override def init(): Future[Unit] = Future.successful(())

      override def apply(documents: Seq[SampleDocument])
        : Future[Either[Seq[SampleDocument], Seq[SampleDocument]]] = {
        if (documents.map(_.id).contains(failingDocument.id))
          Future.successful(Left(List(failingDocument)))
        else Future.successful(Right(documents))
      }
    }

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(queue = queue, indexer = indexer, sender = sender) {
          pipelineStream =>
            sendNotificationToSQS(
              queue = queue,
              message = 1
            )
            sendNotificationToSQS(
              queue = queue,
              message = 2
            )
            pipelineStream.foreach("test stream", msg => {
              if (Integer.parseInt(msg.body) == 1)
                Future.successful(documentsFailingMessage)
              else Future.successful(documentsSuccessfulMessage)
            })
            eventually {
              sender.messages.map(_.body) should contain theSameElementsAs documentsSuccessfulMessage
                .map(_.id)
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, 1)
            }
        }
    }
  }

  it("does not delete a message if some of the process results fail sending") {
    val index = createIndex
    val documentsFailingMessage = (1 to 2)
      .map(_ => createDocument)
      .toList
    val documentsSuccessfulMessage = (1 to 3)
      .map(_ => createDocument)
      .toList

    val sentDocuments = documentsFailingMessage.tail ++ documentsSuccessfulMessage

    val failingDocument = documentsFailingMessage.head

    val brokenSender = new MemoryMessageSender() {
      override def send(body: String): Try[Unit] = {
        if (failingDocument.id.equals(body))
          Failure(new Exception("BOOM!"))
        else super.send(body)
      }
    }

    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = brokenSender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = 1
          )
          sendNotificationToSQS(
            queue = queue,
            message = 2
          )
          pipelineStream.foreach("test stream", msg => {
            if (Integer.parseInt(msg.body) == 1)
              Future.successful(documentsFailingMessage)
            else Future.successful(documentsSuccessfulMessage)
          })

          assertElasticsearchEventuallyHas(
            index = index,
            failingDocument +: sentDocuments: _*)
          eventually {
            brokenSender.messages
              .map(_.body)
              .distinct should contain theSameElementsAs sentDocuments.map(_.id)
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  it(
    "processes a message and does not ingest if result of process is an empty List") {
    val index = createIndex
    val document = createDocument

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 10.seconds) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(index),
          sender = sender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = document
          )
          pipelineStream.foreach("test stream", _ => Future.successful(Nil))

          eventually {
            assertElasticsearchEmpty(index = index)
            sender.messages.map(_.body) shouldBe empty
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it(
    "does not delete the message if the processFunction throws an exception not wrapped in a future") {
    val document = createDocument

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = new MemoryIndexer[SampleDocument](),
          sender = sender) { pipelineStream =>
          sendNotificationToSQS(
            queue = queue,
            message = document
          )
          pipelineStream.foreach(
            "test stream",
            _ => throw new Exception("Boom!"))

          eventually {
            sender.messages.map(_.body) shouldBe empty
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)
          }
        }
    }
  }

  it("ingests lots of documents") {
    val index = createIndex
    val documents = (1 to 250).map(_ => createDocument)

    val pipelineStorageConfig = PipelineStorageConfig(
      batchSize = 150,
      flushInterval = 10 seconds,
      parallelism = 10
    )

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 30.seconds) {
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
                .map(List(_)))
          assertElasticsearchEventuallyHas(index = index, documents: _*)
          eventually(Timeout(scaled(60 seconds))) {
            sender.messages.map(_.body) should contain theSameElementsAs documents
              .map(_.id)
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("leaves a message on the queue if it fails processing") {
    val index = createIndex
    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(queue = queue, indexer = indexer(index)) {
          pipelineStream =>
            sendInvalidJSONto(queue)
            pipelineStream.foreach(
              "test stream",
              message =>
                Future
                  .fromTry(JsonUtil.fromJson[SampleDocument](message.body))
                  .map(List(_)))
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
              .foreach("test stream", _ => Future.successful(Nil))
              .failed) { exception =>
            exception shouldBe a[RuntimeException]
          }
      }
    }
  }

  it("does not delete from the queue documents that failed indexing") {
    val successfulDocuments = (1 to 5).map { _ =>
      createDocument
    }
    val failingDocuments = (1 to 5).map { _ =>
      val doc = createDocument
      (doc.id, doc)
    }.toMap
    val documents = successfulDocuments ++ failingDocuments.values
    val indexer = new Indexer[SampleDocument] {
      override def init(): Future[Unit] = Future.successful(())

      override def apply(documents: Seq[SampleDocument])
        : Future[Either[Seq[SampleDocument], Seq[SampleDocument]]] = {
        Future.successful(
          Left(documents.filter(d => failingDocuments.keySet.contains(d.id))))
      }
    }

    val sender = new MemoryMessageSender

    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
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
                  .map(List(_)))
            eventually {
              sender.messages.map(_.body) should contain theSameElementsAs successfulDocuments
                .map(_.id)
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 5)
            }
        }
    }
  }

  it("leaves a document on the queue if it can't send an onward message") {
    val document = createDocument

    val brokenSender = new MemoryMessageSender() {
      override def send(body: String): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        withPipelineStream(
          queue = queue,
          indexer = indexer(createIndex),
          sender = brokenSender) { pipelineStream =>
          sendNotificationToSQS(queue, document)

          def runStream =
            () =>
              pipelineStream.foreach(
                "test stream",
                _ => Future.successful(List(document))
            )

          whenReady(runStream()) { _ =>
            whenReady(runStream()) { _ =>
              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, size = 1)
              }
            }
          }
        }
    }
  }

  describe("takeListsOfCompleteBundles") {
    it("regroups bundles by messageId") {
      withActorSystem { implicit ac =>
        val messages = (1 to 5).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val bundlesMap = messages
          .map(
            message =>
              message -> (1 to 2).map(
                i =>
                  Bundle(
                    message,
                    createDocumentWith(id = message.messageId() + i),
                    numberOfItems = 2)))
          .toMap

        val result = Source(Random.shuffle(bundlesMap.values.flatten.toList))
          .via(PipelineStorageStream
            .takeListsOfCompleteBundles(Integer.MAX_VALUE, 100 millisecond))
          .runWith(Sink.seq)

        whenReady(result) { res: Seq[List[Bundle[SampleDocument]]] =>
          res.map(
            bundles =>
              bundles should contain theSameElementsAs bundlesMap(
                bundles.head.message))
        }

      }
    }

    it(
      "regroups bundles by messageId and filters the bundles that don't belong to a complete group") {
      withActorSystem { implicit ac =>
        val successfulMessages = (1 to 2).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val completeBundlesMap = successfulMessages
          .map(
            message =>
              message -> (1 to 2).map(
                i =>
                  Bundle(
                    message,
                    createDocumentWith(id = message.messageId() + i),
                    numberOfItems = 2)))
          .toMap
        val failingMessages = (3 to 5).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val failingBundles = failingMessages.map(
          message =>
            Bundle(
              message,
              createDocumentWith(id = message.messageId()),
              numberOfItems = 2))

        val result = Source(
          Random.shuffle(
            completeBundlesMap.values.flatten.toList ++ failingBundles))
          .via(PipelineStorageStream
            .takeListsOfCompleteBundles(Integer.MAX_VALUE, 100 millisecond))
          .runWith(Sink.seq)

        whenReady(result) { res: Seq[List[Bundle[SampleDocument]]] =>
          res.map(
            bundles =>
              bundles should contain theSameElementsAs completeBundlesMap(
                bundles.head.message))
        }

      }
    }

    it("can receive more messageIds than maxSubStreams") {
      withActorSystem { implicit ac =>
        val messages = (1 to 5).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val bundles = messages.map(
          message =>
            Bundle(
              message,
              createDocumentWith(id = message.messageId()),
              numberOfItems = 1))
        // set maxSubstreams lower than the number of messages
        val maxSubStreams = 3
        val (queue, result) = Source
          .queue[Bundle[SampleDocument]](
            bufferSize = maxSubStreams,
            overflowStrategy = OverflowStrategy.backpressure)
          .viaMat(PipelineStorageStream.takeListsOfCompleteBundles(
            maxSubStreams,
            100 millisecond))(Keep.left)
          .mapConcat(identity)
          .toMat(Sink.seq)(Keep.both)
          .run()

        bundles.map(queue.offer)
        queue.complete()

        whenReady(result) { res =>
          res shouldBe bundles
        }
      }
    }

    it(
      "can receive more messageIds than maxSubStreams even if some aren't complete") {
      withActorSystem { implicit ac =>
        val successfulMessages = (1 to 2).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val failingMessages = (3 to 5).map(i =>
          Message.builder().messageId(i.toString).body(i.toString).build())
        val successfulBundles = successfulMessages.map(
          message =>
            Bundle(
              message,
              createDocumentWith(id = message.messageId()),
              numberOfItems = 1))
        //  number of items doesn't match on the Bundles
        val failingBundles = failingMessages.map(
          message =>
            Bundle(
              message,
              createDocumentWith(id = message.messageId()),
              numberOfItems = 2))

        val maxSubStreams = 3

        val (queue, result) = Source
          .queue[Bundle[SampleDocument]](
            bufferSize = maxSubStreams,
            overflowStrategy = OverflowStrategy.backpressure)
          .viaMat(PipelineStorageStream.takeListsOfCompleteBundles(
            maxSubStreams,
            100 millisecond))(Keep.left)
          .mapConcat(identity)
          .toMat(Sink.seq)(Keep.both)
          .run()

        failingBundles.map(queue.offer)
        // wait for timeout to expire
        Thread.sleep((300 millisecond).toMillis)
        successfulBundles.map(queue.offer)
        queue.complete()

        whenReady(result) { res =>
          res shouldBe successfulBundles
        }
      }
    }
  }

  describe("batchRetrieveFlow") {
    it("retrieves multiple documents") {
      val documents =
        (1 to 5).map(i => (i.toString, createDocumentWith(id = i.toString)))
      val retriever = new MemoryRetriever[SampleDocument](
        collection.mutable.Map(documents: _*))

      withActorSystem { implicit ac =>
        val expectedResult = documents.map {
          case (k, doc) =>
            (Message.builder().messageId(k).body(k).build(), doc)
        }
        val messages = expectedResult.map {
          case (message, _) => (message, NotificationMessage(message.body()))
        }

        val result = Source(messages)
          .via(PipelineStorageStream
            .batchRetrieveFlow(pipelineStorageConfig, retriever))
          .runWith(Sink.seq)

        whenReady(result) { res: Seq[(Message, SampleDocument)] =>
          res shouldBe expectedResult
        }
      }
    }

    it("filters out documents that it fails to retrieve") {
      val successfulDocuments =
        (1 to 3).map(i => (i.toString, createDocumentWith(id = i.toString)))
      val retriever = new MemoryRetriever[SampleDocument](
        collection.mutable.Map(successfulDocuments: _*))
      val failingDocuments =
        (4 to 5).map(i => (i.toString, createDocumentWith(id = i.toString)))
      val documents = successfulDocuments ++ failingDocuments

      withActorSystem { implicit ac =>
        val messageDocsMap = documents.map {
          case (k, doc) =>
            (Message.builder().messageId(k).body(k).build(), doc)
        }
        val messages = messageDocsMap.map {
          case (message, _) => (message, NotificationMessage(message.body()))
        }
        val expectedResult = messageDocsMap.filter {
          case (message, doc) =>
            successfulDocuments.contains((message.body, doc))
        }

        val result = Source(messages)
          .via(PipelineStorageStream
            .batchRetrieveFlow(pipelineStorageConfig, retriever))
          .runWith(Sink.seq)

        whenReady(result) { res: Seq[(Message, SampleDocument)] =>
          res shouldBe expectedResult
        }
      }
    }
  }

}
