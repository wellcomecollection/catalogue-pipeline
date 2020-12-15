package weco.catalogue.transformer

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.MemoryIndexer
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.transformer.example.{
  ExampleData,
  ExampleTransformerWorker,
  ValidExampleData
}

class TransformerWorkerTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with PipelineStorageStreamFixtures
    with IdentifiersGenerators {

  it("if it can't look up the source data, it fails") {
    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorker(queue) { _ =>
          sendNotificationToSQS(queue, Version("A", 1))

          eventually {
            assertQueueHasSize(dlq, size = 1)
            assertQueueEmpty(queue)
          }
        }
    }
  }

  it("uses the version from the store, not the message") {
    val storeVersion = 5
    val messageVersion = storeVersion - 1

    val sourceStore = MemoryVersionedStore[String, ExampleData](
      initialEntries = Map(
        Version("A", storeVersion) -> ValidExampleData(createSourceIdentifier)
      )
    )

    val workIndexer = new MemoryIndexer[Work[Source]]()

    withLocalSqsQueue() { queue =>
      withWorker(queue, workIndexer = workIndexer, sourceStore = sourceStore) {
        _ =>
          sendNotificationToSQS(queue, Version("A", messageVersion))

          eventually {
            workIndexer.index.values.map { _.version }.toSeq shouldBe Seq(
              storeVersion)
          }
      }
    }
  }

  def withWorker[R](
    queue: Queue,
    workIndexer: MemoryIndexer[Work[Source]] = new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    sourceStore: MemoryVersionedStore[String, ExampleData] =
      MemoryVersionedStore[String, ExampleData](initialEntries = Map.empty)
  )(
    testWith: TestWith[Unit, R]
  ): R =
    withPipelineStream[Work[Source], R](
      queue = queue,
      indexer = workIndexer,
      sender = workKeySender) { pipelineStream =>
      val worker = new ExampleTransformerWorker(
        pipelineStream = pipelineStream,
        sourceStore = sourceStore
      )

      worker.run()

      testWith(())
    }
}
