package uk.ac.wellcome.platform.transformer.mets.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{
  IdentifierType,
  License,
  Work,
  WorkState
}
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class MetsTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, String],
      MetsSourcePayload,
      MetsSourceData]
    with MetsGenerators
    with S3ObjectLocationGenerators {
  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, String], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, String]()
    )

  override def createPayload(
    implicit store: MemoryTypedStore[S3ObjectLocation, String])
    : MetsSourcePayload = {
    val bibId = randomAlphanumeric()

    val metsXML = metsXmlWith(
      recordIdentifier = bibId,
      accessConditionStatus = Some("Open"),
      license = Some(License.CC0)
    )

    val location = S3ObjectLocation(
      bucket = createBucketName,
      key = s"digitised/$bibId/v1/METS.xml"
    )

    store.put(location)(metsXML) shouldBe a[Right[_, _]]

    MetsSourcePayload(
      id = bibId,
      sourceData = MetsSourceData(
        bucket = location.bucket,
        path = s"digitised/$bibId/v1",
        version = 1,
        file = "METS.xml",
        createdDate = Instant.now(),
        deleted = false
      ),
      version = 1
    )
  }

  override def createBadPayload(
    implicit store: MemoryTypedStore[S3ObjectLocation, String])
    : MetsSourcePayload =
    MetsSourcePayload(
      id = randomAlphanumeric(),
      sourceData = MetsSourceData(
        bucket = createBucketName,
        path = "",
        version = 1,
        file = randomAlphanumeric(),
        createdDate = Instant.now(),
        deleted = false
      ),
      version = 1
    )

  override implicit val encoder: Encoder[MetsSourcePayload] =
    deriveConfiguredEncoder[MetsSourcePayload]

  override def assertMatches(p: MetsSourcePayload, w: Work[WorkState.Source])(
    implicit context: MemoryTypedStore[S3ObjectLocation, String]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType("mets")
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
