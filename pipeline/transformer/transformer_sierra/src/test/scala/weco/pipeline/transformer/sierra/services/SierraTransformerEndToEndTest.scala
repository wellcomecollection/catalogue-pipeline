package weco.pipeline.transformer.sierra.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpecLike
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.sierra.SierraTransformer
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.sierra.generators.SierraIdentifierGenerators
import weco.sierra.models.identifiers.SierraBibNumber
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SierraTransformerEndToEndTest
    extends AnyFunSpecLike
    with SierraIdentifierGenerators
    with PipelineStorageStreamFixtures
    with S3ObjectLocationGenerators
    with Eventually
    with IntegrationPatience
    with SierraRecordGenerators {
  it("receives a payload, transforms into a work and sends it on") {
    withWorker() {
      case (_, QueuePair(queue, dlq), indexer, messageSender, store) =>
        val sierraPayload =
          createAndStorePayloadWith(createSierraBibNumber.withoutCheckDigit, 1)(
            store)
        sendNotificationToSQS(queue, sierraPayload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          indexer.index should have size 1

          val sentKeys = messageSender.messages.map { _.body }
          val storedKeys = indexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys

          val storedWork = indexer.index.values.head
          storedWork.sourceIdentifier.identifierType shouldBe IdentifierType.SierraSystemNumber
          storedWork.sourceIdentifier.value shouldBe SierraBibNumber(
            sierraPayload.id).withCheckDigit
        }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] = new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryTypedStore[S3ObjectLocation, SierraTransformable] =
      MemoryTypedStore(initialEntries = Map.empty)
  )(
    testWith: TestWith[
      (TransformerWorker[SierraSourcePayload, SierraTransformable, String],
       QueuePair,
       MemoryIndexer[Work[Source]],
       MemoryMessageSender,
       MemoryTypedStore[S3ObjectLocation, SierraTransformable]),
      R]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 5.second) {
      case q @ QueuePair(queue, _) =>
        withPipelineStream[Work[Source], R](
          queue = queue,
          indexer = workIndexer,
          sender = workKeySender) { pipelineStream =>
          val retriever =
            new MemoryRetriever[Work[Source]](index = mutable.Map())

          val worker = new TransformerWorker[
            SierraSourcePayload,
            SierraTransformable,
            String](
            transformer =
              (id: String, transformable: SierraTransformable, version: Int) =>
                SierraTransformer(transformable, version).toEither,
            pipelineStream = pipelineStream,
            retriever = retriever,
            sourceDataRetriever = new SierraSourceDataRetriever(store)
          )
          worker.run()

          testWith((worker, q, workIndexer, workKeySender, store))

        }
    }
  def createAndStorePayloadWith(id: String, version: Int)(
    store: MemoryTypedStore[S3ObjectLocation, SierraTransformable])
    : SierraSourcePayload = {

    val transformable = createSierraTransformableWith(
      bibRecord = createSierraBibRecordWith(id = SierraBibNumber(id))
    )
    val location = createS3ObjectLocation

    store.put(location)(transformable) shouldBe a[Right[_, _]]

    SierraSourcePayload(
      id = transformable.sierraId.withoutCheckDigit,
      location = location,
      version = version
    )

  }
}
