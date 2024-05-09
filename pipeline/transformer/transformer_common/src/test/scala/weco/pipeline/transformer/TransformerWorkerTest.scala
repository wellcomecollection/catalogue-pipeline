package weco.pipeline.transformer

import org.scalatest.EitherValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkData}
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.example._
import weco.pipeline.transformer.result.Result
import weco.pipeline_storage.RetrieverNotFoundException
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.storage.Version
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryVersionedStore

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Try}

class TransformerWorkerTest
    extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with PipelineStorageStreamFixtures
    with S3ObjectLocationGenerators
    with IdentifiersGenerators
    with EitherValues {

  it("if it can't look up the source data, it fails") {
    withWorker() {
      case (_, QueuePair(queue, dlq), _, _, _) =>
        sendNotificationToSQS(queue, Version("A", 1))

        eventually {
          assertQueueHasSize(dlq, size = 1)
          assertQueueEmpty(queue)
        }
    }
  }

  it("uses the version from the store, not the message") {
    val storeVersion = 5
    val messageVersion = storeVersion - 1

    val location = createS3ObjectLocation
    val data = ValidExampleData(
      id = createSourceIdentifier,
      title = randomAlphanumeric()
    )

    val sourceStore = MemoryVersionedStore[S3ObjectLocation, ExampleData](
      initialEntries = Map(
        Version(location, storeVersion) -> data
      )
    )

    val payload = CalmSourcePayload(
      id = data.id.toString,
      location = location,
      version = messageVersion
    )

    withWorker(store = sourceStore) {
      case (_, QueuePair(queue, dlq), workIndexer, _, _) =>
        sendNotificationToSQS(queue, payload)

        eventually {
          workIndexer.index.values.map { _.version }.toSeq shouldBe Seq(
            storeVersion
          )
        }
    }
  }

  it("transforms a work, indexes it, and removes it from the queue") {
    withWorker() {
      case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
        val payload = createPayload(store)
        sendNotificationToSQS(queue, payload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          workIndexer.index should have size 1

          val sentKeys = workKeySender.messages.map { _.body }
          val storedKeys = workIndexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys

          workIndexer.index.values.head.sourceIdentifier.value shouldBe payload.id
          workIndexer.index.values.head.version shouldBe payload.version

        }
    }
  }

  describe("decides when to skip sending a work") {
    it("skips sending a Work if there's a strictly newer Work already stored") {
      withWorker() {
        case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
          val id = randomAlphanumeric()
          val oldPayload = createPayloadWith(id = id, version = 1)(store)
          val newPayload = createPayloadWith(id = id, version = 2)(store)
          // First we transform the new payload, and check it stores successfully.
          sendNotificationToSQS(queue, newPayload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }

          // Now we transform the new payload, and check nothing new got send
          sendNotificationToSQS(queue, oldPayload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }
      }
    }

    it(
      "re-sends a Work if the stored Work has the same version but different data"
    ) {
      val id = randomAlphanumeric()

      withWorker() {
        case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
          // Transform the first payload, and check it stores successfully.
          val payloadA = createPayloadWith(id = id, version = 1)(store)
          sendNotificationToSQS(queue, payloadA)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }

          // Transform the second payload, and check an ID gets re-sent
          val payloadB = createPayloadWith(id = id, version = 1)(store)
          sendNotificationToSQS(queue, payloadB)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 2
          }
      }
    }

    it(
      "resends a work if it has different version and different info in state"
    ) {
      val id = randomAlphanumeric()
      val modifiedTime = Instant.now()
      val sourceIdentifier = createCalmSourceIdentifier
      val stateChangingTransformer = new Transformer[ExampleData] {
        override def apply(
          id: String,
          sourceData: ExampleData,
          version: Int
        ): Result[Work[Source]] =
          Right(
            Work.Visible[Source](
              version = version,
              data = WorkData(title = Some("kjhg")),
              state = Source(
                sourceIdentifier = sourceIdentifier,
                sourceModifiedTime = modifiedTime,
                // merge candidates have a different sourceIdentifier any time this method is called, so the state is always different.
                List(
                  MergeCandidate(
                    identifier = createSourceIdentifier,
                    reason = ""
                  )
                )
              )
            )
          )
      }

      withWorker(
        transformer = stateChangingTransformer,
        visibilityTimeout = 5 seconds
      ) {
        case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
          val payload = createPayloadWith(id = id, version = 1)(store)
          sendNotificationToSQS(queue, payload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }

          sendNotificationToSQS(queue, setPayloadVersion(payload, 2)(store))

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 2
          }
      }
    }

    it(
      "re-sends a Work if the stored Work has the same version and the same data"
    ) {
      withWorker() {
        case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
          val payload = createPayload(store)

          // Transform the first payload, and check it stores successfully.
          sendNotificationToSQS(queue, payload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }

          // Transform the second payload, and check an ID gets re-sent
          sendNotificationToSQS(queue, payload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 2
          }
      }
    }

    it(
      "skips sending a Work if the stored Work has a strictly older Version and the same data"
    ) {

      withWorker() {
        case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
          val oldPayload = createPayloadWith(version = 1)(store)
          val newPayload = setPayloadVersion(oldPayload, version = 2)(store)
          // Transform the first payload, and check it stores successfully.
          sendNotificationToSQS(queue, oldPayload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }

          // Now we transform the new payload, and check nothing new got sent

          sendNotificationToSQS(queue, newPayload)

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
            workIndexer.index should have size 1
            workKeySender.messages should have size 1
          }
      }

    }
  }

  it("transforms multiple works") {
    withWorker() {
      case (_, QueuePair(queue, dlq), workIndexer, workKeySender, store) =>
        val payloads = (1 to 10).map {
          _ =>
            createPayload(store)
        }
        payloads.foreach { sendNotificationToSQS(queue, _) }

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          workIndexer.index should have size payloads.size

          val sentKeys = workKeySender.messages.map { _.body }
          val storedKeys = workIndexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys
        }
    }
  }

  describe("sending failures to the DLQ") {
    it("if it can't parse the JSON on the queue") {
      withWorker() {
        case (_, QueuePair(queue, dlq), _, _, _) =>
          sendInvalidJSONto(queue)

          eventually {
            assertQueueHasSize(dlq, size = 1)
            assertQueueEmpty(queue)
          }
      }
    }

    // Note: this is meaningfully different to the previous test.
    //
    // This message sends a not-JSON string that's wrapped in an SNS notification;
    // the previous tests ends something that didn't come from SNS and can't be
    // parsed as a notification.
    it("if it can't parse the notification on the queue") {
      withWorker() {
        case (_, QueuePair(queue, dlq), _, _, _) =>
          sendNotificationToSQS(queue, "this-is-not-json")

          eventually {
            assertQueueHasSize(dlq, size = 1)
            assertQueueEmpty(queue)
          }
      }
    }

    it("if the payload can't be transformed") {

      withWorker() {
        case (_, QueuePair(queue, dlq), _, _, store) =>
          val payloads = Seq(
            createPayload(store),
            createPayload(store),
            createBadPayload(store)
          )
          payloads.foreach {
            sendNotificationToSQS(queue, _)
          }

          eventually {
            assertQueueHasSize(dlq, size = 1)
            assertQueueEmpty(queue)
          }
      }
    }

    it("if it can't index the work") {
      val brokenIndexer = new MemoryIndexer[Work[Source]]() {
        override def apply(
          documents: Seq[Work[Source]]
        ): Future[Either[Seq[Work[Source]], Seq[Work[Source]]]] =
          Future.failed(new Throwable("BOOM!"))
      }
      withWorker(workIndexer = brokenIndexer) {
        case (worker, QueuePair(queue, dlq), _, workKeySender, store) =>
          val payload = createPayload(store)

          sendNotificationToSQS(queue, payload)

          whenReady(worker.run()) {
            _ =>
              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, size = 1)

                workKeySender.messages shouldBe empty
              }
          }
      }
    }

    it("if it can't send the key of the indexed work") {
      val brokenSender = new MemoryMessageSender() {
        override def send(body: String): Try[Unit] =
          Failure(new Throwable("BOOM!"))
      }

      withWorker(workKeySender = brokenSender) {
        case (worker, QueuePair(queue, dlq), _, _, store) =>
          val payload = createPayload(store)

          sendNotificationToSQS(queue, payload)

          whenReady(worker.run()) {
            _ =>
              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, size = 1)
              }
          }
      }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] =
      new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryVersionedStore[S3ObjectLocation, ExampleData] =
      MemoryVersionedStore[S3ObjectLocation, ExampleData](
        initialEntries = Map.empty
      ),
    transformer: Transformer[ExampleData] = new ExampleTransformer,
    visibilityTimeout: FiniteDuration = 1.second
  )(
    testWith: TestWith[
      (
        TransformerWorker[CalmSourcePayload, ExampleData, String],
        QueuePair,
        MemoryIndexer[Work[Source]],
        MemoryMessageSender,
        MemoryVersionedStore[S3ObjectLocation, ExampleData]
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout) {
      case q @ QueuePair(queue, _) =>
        withPipelineStream[Work[Source], R](
          queue = queue,
          indexer = workIndexer,
          sender = workKeySender
        ) {
          pipelineStream =>
            val retriever =
              new MemoryRetriever[Work[Source]](index = mutable.Map()) {
                override def apply(id: String): Future[Work[Source]] =
                  workIndexer.index.get(id) match {
                    case Some(w) => Future.successful(w)
                    case None =>
                      Future.failed(new RetrieverNotFoundException(id))
                  }
              }

            val worker = new TransformerWorker(
              transformer = transformer,
              pipelineStream = pipelineStream,
              retriever = retriever,
              sourceDataRetriever =
                new ExampleSourcePayloadLookup(sourceStore = store)
            )
            worker.run()

            testWith((worker, q, workIndexer, workKeySender, store))

        }
    }

  def createPayloadWith(id: String = randomAlphanumeric(), version: Int)(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData]
  ): CalmSourcePayload = {
    val data = ValidExampleData(
      id = createSourceIdentifierWith(
        identifierType = IdentifierType.CalmRecordIdentifier,
        value = id
      ),
      title = randomAlphanumeric()
    )

    val location = createS3ObjectLocation

    store.put(Version(location, version))(data) shouldBe a[Right[_, _]]

    CalmSourcePayload(id = id, version = version, location = location)
  }

  def setPayloadVersion(p: CalmSourcePayload, version: Int)(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData]
  ): CalmSourcePayload = {
    val storedData: ExampleData =
      store.get(Version(p.location, p.version)).value.identifiedT

    val location = createS3ObjectLocation
    store.put(Version(location, version))(storedData) shouldBe a[Right[_, _]]

    p.copy(location = location, version = version)
  }

  def createBadPayload(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData]
  ): CalmSourcePayload = {
    val data = InvalidExampleData
    val version = randomInt(from = 1, to = 10)

    val location = createS3ObjectLocation

    store.put(Version(location, version))(data) shouldBe a[Right[_, _]]

    CalmSourcePayload(
      id = randomAlphanumeric(),
      version = version,
      location = location
    )
  }

  // Create a payload which can be transformer
  def createPayload(
    implicit store: MemoryVersionedStore[S3ObjectLocation, ExampleData]
  ): CalmSourcePayload = createPayloadWith(
    version = randomInt(from = 1, to = 10)
  )
}
