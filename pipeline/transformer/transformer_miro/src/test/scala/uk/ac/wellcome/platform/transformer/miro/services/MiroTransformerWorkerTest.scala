package uk.ac.wellcome.platform.transformer.miro.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.EitherValues
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

import scala.concurrent.ExecutionContext.Implicits.global

class MiroTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, MiroRecord],
      MiroSourcePayload,
      (MiroRecord, MiroSourceOverrides, MiroMetadata)]
    with MiroRecordGenerators
    with S3ObjectLocationGenerators
    with EitherValues {

  override def createId: String = createImageNumber

  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, MiroRecord], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, MiroRecord]()
    )

  // The WorkData for a Miro Work is based on the stored version of the work,
  // which is copied onto the Image data.
  override val workDataDependsOnVersion: Boolean = true

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, MiroRecord])
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

  override def setPayloadVersion(p: MiroSourcePayload, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, MiroRecord])
    : MiroSourcePayload = {
    val storedRecord: MiroRecord = store.get(p.location).value.identifiedT

    val location = createS3ObjectLocation
    store.put(location)(storedRecord) shouldBe a[Right[_, _]]

    p.copy(location = location, version = version)
  }

  override def createBadPayload(
    implicit context: MemoryTypedStore[S3ObjectLocation, MiroRecord])
    : MiroSourcePayload =
    MiroSourcePayload(
      id = randomAlphanumeric(),
      version = 1,
      location = createS3ObjectLocation,
      events = List(),
      overrides = None,
      isClearedForCatalogueAPI = chooseFrom(true, false)
    )

  override implicit val encoder: Encoder[MiroSourcePayload] =
    deriveConfiguredEncoder[MiroSourcePayload]

  override def assertMatches(p: MiroSourcePayload, w: Work[WorkState.Source])(
    implicit source: MemoryTypedStore[S3ObjectLocation, MiroRecord]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType.MiroImageNumber
    p.id shouldBe w.sourceIdentifier.value
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String],
    retriever: Retriever[Work[WorkState.Source]])(
    testWith: TestWith[TransformerWorker[MiroSourcePayload,
                                         (MiroRecord,
                                          MiroSourceOverrides,
                                          MiroMetadata),
                                         String],
                       R])(
    implicit miroReadable: MemoryTypedStore[S3ObjectLocation, MiroRecord]): R =
    testWith(
      new MiroTransformerWorker(
        pipelineStream = pipelineStream,
        miroReadable = miroReadable,
        retriever = retriever
      )
    )
}
