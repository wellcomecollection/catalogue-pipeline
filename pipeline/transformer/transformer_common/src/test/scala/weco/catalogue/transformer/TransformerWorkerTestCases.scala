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
import uk.ac.wellcome.pipeline_storage.{MemoryIndexer, PipelineStorageStream}

import scala.concurrent.Future
import scala.util.{Failure, Try}

trait TransformerWorkerTestCases[Context, Payload, SourceData]
  extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with PipelineStorageStreamFixtures {

  def withContext[R](testWith: TestWith[Context, R]): R

  // Create a payload which can be transformer
  def createPayload(implicit context: Context): Payload

  // Create a payload which cannot be transformed
  def createBadPayload(implicit context: Context): Payload

  implicit val encoder: Encoder[Payload]

  def id(p: Payload): String
  def version(p: Payload): Int

  def assertMatches(p: Payload, w: Work[Source])(implicit context: Context)

  def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage, Work[Source], String])(
    testWith: TestWith[TransformerWorker[SourceData, String], R]
  )(
    implicit context: Context
  ): R

  describe("behaves as a TransformerWorker") {
    it("transforms works, indexes them, and removes them from the queue") {
      withContext { implicit context =>
        val payloads = (1 to 5).map { _ => createPayload }

        val workIndexer = new MemoryIndexer[Work[Source]]()
        val workKeySender = new MemoryMessageSender()

        withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
          withWorkerImpl(queue, workIndexer, workKeySender) { _ =>
            payloads.foreach { sendNotificationToSQS(queue, _) }

            eventually {
              assertQueueEmpty(dlq)
              assertQueueEmpty(queue)

              workIndexer.index should have size payloads.size

              val sentKeys = workKeySender.messages.map { _.body }
              val storedKeys = workIndexer.index.keys
              sentKeys should contain theSameElementsAs storedKeys

              payloads.foreach { p =>
                assertMatches(p, workIndexer.index(id(p)))
              }
            }
          }
        }
      }
    }

    describe("sending failures to the DLQ") {
      it("if it can't parse the JSON on the queue") {
        withContext { implicit context =>
          withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
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

      it("if the payload can't be transformed") {
        withContext { implicit context =>
          val payloads = Seq(
            createPayload, createPayload, createBadPayload
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
          override def index(documents: Seq[Work[Source]]): Future[Either[Seq[Work[Source]], Seq[Work[Source]]]] =
            Future.failed(new Throwable("BOOM!"))
        }

        val workKeySender = new MemoryMessageSender

        withContext { implicit context =>
          val payload = createPayload

          withLocalSqsQueuePair() {
            case QueuePair(queue, dlq) =>
              withWorkerImpl(queue, workIndexer = brokenIndexer, workKeySender = workKeySender) { _ =>
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
              withWorkerImpl(queue, workKeySender = brokenSender) {
                _ =>
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
      withWorker(pipelineStream) { worker =>
        worker.run()

        testWith(())
      }
    }
}
