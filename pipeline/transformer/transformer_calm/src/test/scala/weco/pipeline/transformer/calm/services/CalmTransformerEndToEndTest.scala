package weco.pipeline.transformer.calm.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpecLike
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.calm.CalmTransformer
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class CalmTransformerEndToEndTest
    extends AnyFunSpecLike
    with PipelineStorageStreamFixtures
    with CalmRecordGenerators
    with S3ObjectLocationGenerators
    with Eventually
    with IntegrationPatience {
  it("receives a payload, transforms into a work and sends it on") {
    withWorker() {
      case (_, QueuePair(queue, dlq), indexer, messageSender, store) =>
        val calmPayload = createAndStorePayloadWith("id", 1)(store)
        sendNotificationToSQS(queue, calmPayload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          indexer.index should have size 1

          val sentKeys = messageSender.messages.map { _.body }
          val storedKeys = indexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys
          val storedWork = indexer.index.values.head
          storedWork.sourceIdentifier.identifierType shouldBe IdentifierType.CalmRecordIdentifier
          storedWork.sourceIdentifier.value shouldBe calmPayload.id
        }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] =
      new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryTypedStore[S3ObjectLocation, CalmRecord] =
      MemoryTypedStore[S3ObjectLocation, CalmRecord](initialEntries = Map.empty)
  )(
    testWith: TestWith[
      (
        TransformerWorker[CalmSourcePayload, CalmSourceData, String],
        QueuePair,
        MemoryIndexer[Work[Source]],
        MemoryMessageSender,
        MemoryTypedStore[S3ObjectLocation, CalmRecord]
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case q @ QueuePair(queue, _) =>
        withPipelineStream[Work[Source], R](
          queue = queue,
          indexer = workIndexer,
          sender = workKeySender
        ) {
          pipelineStream =>
            val retriever =
              new MemoryRetriever[Work[Source]](index = mutable.Map())

            val worker =
              new TransformerWorker[CalmSourcePayload, CalmSourceData, String](
                transformer = CalmTransformer,
                pipelineStream = pipelineStream,
                transformedWorkRetriever = retriever,
                sourceDataRetriever = new CalmSourceDataRetriever(store)
              )
            worker.run()

            testWith((worker, q, workIndexer, workKeySender, store))

        }
    }
  def createAndStorePayloadWith(id: String, version: Int)(
    store: MemoryTypedStore[S3ObjectLocation, CalmRecord]
  ): CalmSourcePayload = {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )

    val location = createS3ObjectLocation

    store.put(location)(record.copy(id = id)) shouldBe a[Right[_, _]]

    CalmSourcePayload(id = id, location = location, version = version)
  }
}
