package weco.pipeline.transformer.tei.service

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpecLike
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiMetadata}
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.tei.TeiTransformer
import weco.pipeline.transformer.tei.generators.TeiGenerators
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TeiTransformerEndToEndTest
    extends AnyFunSpecLike
    with TeiGenerators
    with PipelineStorageStreamFixtures
    with S3ObjectLocationGenerators
    with Eventually
    with IntegrationPatience {
  it("receives a payload, transforms into a work and sends it on") {
    withWorker() {
      case (_, QueuePair(queue, dlq), indexer, messageSender, store) =>
        val title = "This is the title"
        val TeiPayload = createAndStorePayloadWith(
          id = "MS_WMS_1",
          version = 1,
          title = title
        )(store)
        sendNotificationToSQS(queue, TeiPayload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          indexer.index should have size 1

          val sentKeys = messageSender.messages.map { _.body }
          val storedKeys = indexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys

          val storedWork = indexer.index.values.head
          storedWork.sourceIdentifier.identifierType shouldBe IdentifierType.Tei
          TeiPayload.id shouldBe storedWork.sourceIdentifier.value
        }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] =
      new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryTypedStore[S3ObjectLocation, String] = MemoryTypedStore(
      initialEntries = Map.empty
    )
  )(
    testWith: TestWith[
      (
        TransformerWorker[TeiSourcePayload, TeiMetadata, String],
        QueuePair,
        MemoryIndexer[Work[Source]],
        MemoryMessageSender,
        MemoryTypedStore[S3ObjectLocation, String]
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
              new TransformerWorker[TeiSourcePayload, TeiMetadata, String](
                transformer = new TeiTransformer(store),
                pipelineStream = pipelineStream,
                retriever = retriever,
                sourceDataRetriever = new TeiSourceDataRetriever
              )
            worker.run()

            testWith((worker, q, workIndexer, workKeySender, store))

        }
    }
  def createAndStorePayloadWith(id: String, title: String, version: Int)(
    store: MemoryTypedStore[S3ObjectLocation, String]
  ): TeiSourcePayload = {

    val xmlString =
      teiXml(id, refNo = idnoMsId(title))
        .toString()

    val location = S3ObjectLocation(
      bucket = createBucketName,
      key = s"tei_files/$id/Tei.xml"
    )

    store.put(location)(xmlString) shouldBe a[Right[_, _]]
    TeiSourcePayload(id, TeiChangedMetadata(location, Instant.now()), version)
  }
}
