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

trait TransformerWorkerTestCases[Context, Payload, SourceData]
  extends AnyFunSpec
    with Eventually
    with IntegrationPatience
    with PipelineStorageStreamFixtures {

  def withContext[R](testWith: TestWith[Context, R]): R

  def createPayload(implicit context: Context): Payload

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
