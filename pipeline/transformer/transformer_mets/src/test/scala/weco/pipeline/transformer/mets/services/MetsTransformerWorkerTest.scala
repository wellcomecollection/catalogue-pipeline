package weco.pipeline.transformer.mets.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.concurrent.IntegrationPatience
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.generators.MetsSourceDataGenerators
import weco.catalogue.source_model.mets.{MetsFileWithImages, MetsSourceData}
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{TransformerWorker, TransformerWorkerTestCases}
import weco.pipeline.transformer.mets.fixtures.MetsGenerators
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.memory.MemoryTypedStore

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class MetsTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, String],
      MetsSourcePayload,
      MetsSourceData]
    with MetsGenerators
    with MetsSourceDataGenerators
    with S3ObjectLocationGenerators
    with IntegrationPatience {
  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, String], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, String]()
    )

  override def createId: String = createBibNumber

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, String])
    : MetsSourcePayload = {
    val metsXML = metsXmlWith(
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

  override def setPayloadVersion(p: MetsSourcePayload, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, String])
    : MetsSourcePayload =
    p.copy(
      sourceData =
        p.sourceData.asInstanceOf[MetsFileWithImages].copy(version = version)
    )

  override def createBadPayload(
    implicit store: MemoryTypedStore[S3ObjectLocation, String])
    : MetsSourcePayload =
    MetsSourcePayload(
      id = randomAlphanumeric(),
      sourceData = createMetsSourceData,
      version = 1
    )

  override implicit val encoder: Encoder[MetsSourcePayload] =
    deriveConfiguredEncoder[MetsSourcePayload]

  override def assertMatches(p: MetsSourcePayload, w: Work[WorkState.Source])(
    implicit context: MemoryTypedStore[S3ObjectLocation, String]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType.METS
    p.id shouldBe w.sourceIdentifier.value
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String],
    retriever: Retriever[Work[WorkState.Source]])(
    testWith: TestWith[
      TransformerWorker[MetsSourcePayload, MetsSourceData, String],
      R])(
    implicit metsXmlStore: MemoryTypedStore[S3ObjectLocation, String]): R =
    testWith(
      new MetsTransformerWorker(
        pipelineStream = pipelineStream,
        metsXmlStore = metsXmlStore,
        retriever = retriever
      )
    )
}
