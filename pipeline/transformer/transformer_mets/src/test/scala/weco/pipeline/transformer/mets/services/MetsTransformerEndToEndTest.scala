package weco.pipeline.transformer.mets.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpecLike
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.{MetsFileWithImages, MetsSourceData}
import weco.catalogue.source_model.Implicits._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.mets.generators.GoobiMetsGenerators
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.sierra.generators.SierraIdentifierGenerators
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.memory.MemoryTypedStore

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MetsTransformerEndToEndTest
    extends AnyFunSpecLike
    with SierraIdentifierGenerators
    with PipelineStorageStreamFixtures
    with S3ObjectLocationGenerators
    with Eventually
    with IntegrationPatience
    with GoobiMetsGenerators {
  it("receives a payload, transforms into a work and sends it on") {
    withWorker() {
      case (_, QueuePair(queue, dlq), indexer, messageSender, store) =>
        val metsPayload =
          createAndStorePayloadWith(createSierraBibNumber.withCheckDigit, 1)(
            store
          )
        sendNotificationToSQS(queue, metsPayload)

        eventually {
          assertQueueEmpty(dlq)
          assertQueueEmpty(queue)

          indexer.index should have size 1

          val sentKeys = messageSender.messages.map { _.body }
          val storedKeys = indexer.index.keys
          sentKeys should contain theSameElementsAs storedKeys

          val storedWork = indexer.index.values.head
          storedWork.sourceIdentifier.identifierType shouldBe IdentifierType.METS
          metsPayload.id shouldBe storedWork.sourceIdentifier.value
        }
    }
  }

  def withWorker[R](
    workIndexer: MemoryIndexer[Work[Source]] =
      new MemoryIndexer[Work[Source]](),
    workKeySender: MemoryMessageSender = new MemoryMessageSender(),
    store: MemoryTypedStore[S3ObjectLocation, String] =
      MemoryTypedStore[S3ObjectLocation, String](initialEntries = Map.empty)
  )(
    testWith: TestWith[
      (
        TransformerWorker[MetsSourcePayload, MetsSourceData, String],
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
              new TransformerWorker[MetsSourcePayload, MetsSourceData, String](
                transformer = new MetsXmlTransformer(store),
                pipelineStream = pipelineStream,
                transformedWorkRetriever = retriever,
                sourceDataRetriever = new MetsSourceDataRetriever
              )
            worker.run()

            testWith((worker, q, workIndexer, workKeySender, store))

        }
    }
  def createAndStorePayloadWith(id: String, version: Int)(
    store: MemoryTypedStore[S3ObjectLocation, String]
  ): MetsSourcePayload = {

    val metsXML = goobiMetsXmlWith(
      recordIdentifier = id,
      accessConditionStatus = Some("Open"),
      license = Some(License.CC0)
    )

    val location = S3ObjectLocation(
      bucket = createBucketName,
      key = s"digitised/$id/v1/METS.xml"
    )

    store.put(location)(metsXML) shouldBe a[Right[_, _]]

    MetsSourcePayload(
      id = id,
      sourceData = MetsFileWithImages(
        root = S3ObjectLocationPrefix(
          bucket = location.bucket,
          keyPrefix = s"digitised/$id"
        ),
        filename = "v1/METS.xml",
        manifestations = List.empty,
        version = version,
        createdDate = Instant.now()
      ),
      version = 1
    )

  }
}
