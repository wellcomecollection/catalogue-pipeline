package weco.catalogue.transformer

import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.pipeline_storage.{
  MemoryIndexer,
  MemoryRetriever,
  PipelineStorageStream,
  Retriever,
  RetrieverNotFoundException
}
import weco.catalogue.source_model.SourcePayload

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

trait TransformerWorkerTestCases[Context, Payload <: SourcePayload, SourceData]
    extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with PipelineStorageStreamFixtures {

  def withContext[R](testWith: TestWith[Context, R]): R

  def createId: String = randomAlphanumeric()

  // Create a payload which can be transformer
  def createPayload(implicit context: Context): Payload =
    createPayloadWith(
      version = randomInt(from = 1, to = 10)
    )

  def createPayloadWith(id: String = createId, version: Int)(implicit context: Context): Payload

  def setPayloadVersion(p: Payload, version: Int)(implicit context: Context): Payload

  // Create a payload which cannot be transformed
  def createBadPayload(implicit context: Context): Payload

  implicit val encoder: Encoder[Payload]

  def assertMatches(p: Payload, w: Work[Source])(implicit context: Context)

  def withWorker[R](pipelineStream: PipelineStorageStream[NotificationMessage,
                                                          Work[Source],
                                                          String],
                    retriever: Retriever[Work[Source]])(
    testWith: TestWith[TransformerWorker[Payload, SourceData, String], R]
  )(
    implicit context: Context
  ): R

  describe("behaves as a TransformerWorker") {
    it("transforms a work, indexes it, and removes it from the queue") {
      withContext { implicit context =>
        val payload = createPayload

        val workIndexer = new MemoryIndexer[Work[Source]]()
        val workKeySender = new MemoryMessageSender()

        withLocalSqsQueuePair() {
          case QueuePair(queue, dlq) =>
            withWorkerImpl(queue, workIndexer, workKeySender) { _ =>
              sendNotificationToSQS(queue, payload)

              eventually {
                assertQueueEmpty(dlq)
                assertQueueEmpty(queue)

                workIndexer.index should have size 1

                val sentKeys = workKeySender.messages.map { _.body }
                val storedKeys = workIndexer.index.keys
                sentKeys should contain theSameElementsAs storedKeys

                assertMatches(payload, workIndexer.index.values.head)
              }
            }
        }
      }
    }

    describe("decides when to skip sending a work") {
      it("skips sending a Work if there's a strictly newer Work already stored") {
        withContext { implicit context =>
          val id = randomAlphanumeric()
          val oldPayload = createPayloadWith(id = id, version = 1)
          val newPayload = createPayloadWith(id = id, version = 2)

          val workIndexer = new MemoryIndexer[Work[Source]]()
          val workKeySender = new MemoryMessageSender()

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workIndexer, workKeySender) { _ =>

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
        }
      }

      it("re-sends a Work if the stored Work has the same version but different data") {
        withContext { implicit context =>
          val id = randomAlphanumeric()

          val workIndexer = new MemoryIndexer[Work[Source]]()
          val workKeySender = new MemoryMessageSender()

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workIndexer, workKeySender) { _ =>

                // Transform the first payload, and check it stores successfully.
                val payloadA = createPayloadWith(id = id, version = 1)
                sendNotificationToSQS(queue, payloadA)

                eventually {
                  assertQueueEmpty(dlq)
                  assertQueueEmpty(queue)
                  workIndexer.index should have size 1
                  workKeySender.messages should have size 1
                }

                // Transform the second payload, and check an ID gets re-sent
                val payloadB = createPayloadWith(id = id, version = 1)
                sendNotificationToSQS(queue, payloadB)

                eventually {
                  assertQueueEmpty(dlq)
                  assertQueueEmpty(queue)
                  workIndexer.index should have size 1
                  workKeySender.messages should have size 2
                }
              }
          }
        }
      }

      it("re-sends a Work if the stored Work has the same version and the same data") {
        withContext { implicit context =>
          val payload = createPayload

          val workIndexer = new MemoryIndexer[Work[Source]]()
          val workKeySender = new MemoryMessageSender()

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workIndexer, workKeySender) { _ =>

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
        }
      }

      it("skips sending a Work if the stored Work has a strictly older Version and the same data") {
        withContext { implicit context =>
          val oldPayload = createPayloadWith(version = 1)
          val newPayload = setPayloadVersion(oldPayload, version = 2)

          val workIndexer = new MemoryIndexer[Work[Source]]()
          val workKeySender = new MemoryMessageSender()

          withLocalSqsQueuePair(visibilityTimeout = 2) {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workIndexer, workKeySender) { _ =>

                // Transform the first payload, and check it stores successfully.
                sendNotificationToSQS(queue, oldPayload)

                eventually {
                  assertQueueEmpty(dlq)
                  assertQueueEmpty(queue)
                  workIndexer.index should have size 1
                  workKeySender.messages should have size 1
                }

                // Now we transform the new payload, and check nothing new got send
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
      }
    }

    it("transforms multiple works") {
      withContext { implicit context =>
        val payloads = (1 to 10).map { _ =>
          createPayload
        }

        val workIndexer = new MemoryIndexer[Work[Source]]()
        val workKeySender = new MemoryMessageSender()

        withLocalSqsQueuePair() {
          case QueuePair(queue, dlq) =>
            withWorkerImpl(queue, workIndexer, workKeySender) { _ =>
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
      }
    }

    describe("sending failures to the DLQ") {
      it("if it can't parse the JSON on the queue") {
        withContext { implicit context =>
          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue) { _ =>
                sendInvalidJSONto(queue)

                eventually {
                  assertQueueHasSize(dlq, size = 1)
                  assertQueueEmpty(queue)
                }
              }
          }
        }
      }

      // Note: this is meaningfully different to the previous test.
      //
      // This message sends a not-JSON string that's wrapped in an SNS notification;
      // the previous tests ends something that didn't come from SNS and can't be
      // parsed as a notification.
      it("if it can't parse the notification on the queue") {
        withContext { implicit context =>
          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue) { _ =>
                sendNotificationToSQS(queue, "this-is-not-json")

                eventually {
                  assertQueueHasSize(dlq, size = 1)
                  assertQueueEmpty(queue)
                }
              }
          }
        }
      }

      it("if the payload can't be transformed") {
        withContext { implicit context =>
          val payloads = Seq(
            createPayload,
            createPayload,
            createBadPayload
          )

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue) { _ =>
                payloads.foreach {
                  sendNotificationToSQS(queue, _)
                }

                eventually {
                  assertQueueHasSize(dlq, size = 1)
                  assertQueueEmpty(queue)
                }
              }
          }
        }
      }

      it("if it can't index the work") {
        val brokenIndexer = new MemoryIndexer[Work[Source]]() {
          override def apply(documents: Seq[Work[Source]])
            : Future[Either[Seq[Work[Source]], Seq[Work[Source]]]] =
            Future.failed(new Throwable("BOOM!"))
        }

        val workKeySender = new MemoryMessageSender

        withContext { implicit context =>
          val payload = createPayload

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(
                queue,
                workIndexer = brokenIndexer,
                workKeySender = workKeySender) { _ =>
                sendNotificationToSQS(queue, payload)

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, size = 1)

                  workKeySender.messages shouldBe empty
                }
              }
          }
        }
      }

      it("if it can't send the key of the indexed work") {
        val brokenSender = new MemoryMessageSender() {
          override def send(body: String): Try[Unit] =
            Failure(new Throwable("BOOM!"))
        }

        withContext { implicit context =>
          val payload = createPayload

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workKeySender = brokenSender) { _ =>
                sendNotificationToSQS(queue, payload)

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, size = 1)
                }
              }
          }
        }
      }
    }
  }

  def withWorkerImpl[R](
    queue: Queue,
    workIndexer: MemoryIndexer[Work[Source]] = new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender()
  )(
    testWith: TestWith[Unit, R]
  )(
    implicit context: Context
  ): R =
    withPipelineStream[Work[Source], R](
      queue = queue,
      indexer = workIndexer,
      sender = workKeySender) { pipelineStream =>
      val retriever = new MemoryRetriever[Work[Source]](index = mutable.Map()) {
        override def apply(id: String): Future[Work[Source]] =
          workIndexer.index.get(id) match {
            case Some(w) => Future.successful(w)
            case None    => Future.failed(new RetrieverNotFoundException(id))
          }
      }

      withWorker(pipelineStream, retriever) { worker =>
        worker.run()

        testWith(())
      }
    }
}
