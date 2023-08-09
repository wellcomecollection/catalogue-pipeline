package weco.pipeline.transformer.miro.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpecLike
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.miro.MiroRecordTransformer
import weco.pipeline.transformer.miro.Implicits._
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MiroTransformerEndToEndTest
    extends AnyFunSpecLike
    with PipelineStorageStreamFixtures
    with S3ObjectLocationGenerators
    with Eventually
    with IntegrationPatience
    with MiroRecordGenerators {
  it("receives a payload, transforms into a work and sends it on") {
    withWorker() {
      case (_, QueuePair(queue, dlq), indexer, messageSender, store) =>
        val miroPayload = createAndStorePayloadWith(createImageNumber, 1)(store)
        sendNotificationToSQS(queue, miroPayload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          indexer.index should have size 1

          val sentKeys = messageSender.messages.map { _.body }
          val storedKeys = indexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys

          val storedWork = indexer.index.values.head
          storedWork.sourceIdentifier.identifierType shouldBe IdentifierType.MiroImageNumber
          miroPayload.id shouldBe storedWork.sourceIdentifier.value
        }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] = new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryTypedStore[S3ObjectLocation, MiroRecord] = MemoryTypedStore(
      initialEntries = Map.empty)
  )(
    testWith: TestWith[(TransformerWorker[MiroSourcePayload,
                                          (MiroRecord,
                                           MiroSourceOverrides,
                                           MiroMetadata),
                                          String],
                        QueuePair,
                        MemoryIndexer[Work[Source]],
                        MemoryMessageSender,
                        MemoryTypedStore[S3ObjectLocation, MiroRecord]),
                       R]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case q @ QueuePair(queue, _) =>
        withPipelineStream[Work[Source], R](
          queue = queue,
          indexer = workIndexer,
          sender = workKeySender) { pipelineStream =>
          val retriever =
            new MemoryRetriever[Work[Source]](index = mutable.Map())

          val worker = new TransformerWorker[
            MiroSourcePayload,
            (MiroRecord, MiroSourceOverrides, MiroMetadata),
            String](
            transformer = new MiroRecordTransformer,
            pipelineStream = pipelineStream,
            retriever = retriever,
            sourceDataRetriever = new MiroSourceDataRetriever(store)
          )
          worker.run()

          testWith((worker, q, workIndexer, workKeySender, store))

        }
    }
  def createAndStorePayloadWith(id: String, version: Int)(
    store: MemoryTypedStore[S3ObjectLocation, MiroRecord])
    : MiroSourcePayload = {

    val record = createMiroRecordWith(imageNumber = id)
    val location = createS3ObjectLocation

    store.put(location)(record) shouldBe a[Right[_, _]]

    MiroSourcePayload(
      id = id,
      version = version,
      location = location,
      events = List(),
      overrides = None,
      isClearedForCatalogueAPI = chooseFrom(true, false)
    )

  }
}
