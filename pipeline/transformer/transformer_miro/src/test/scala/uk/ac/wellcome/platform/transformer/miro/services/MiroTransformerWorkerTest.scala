package uk.ac.wellcome.platform.transformer.miro.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{IdentifierType, Work, WorkState}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

class MiroTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, MiroRecord],
      MiroSourcePayload,
      (MiroRecord, MiroMetadata)]
    with MiroRecordGenerators
    with S3ObjectLocationGenerators {

  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, MiroRecord], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, MiroRecord]()
    )

  override def createPayload(
    implicit store: MemoryTypedStore[S3ObjectLocation, MiroRecord])
    : MiroSourcePayload = {
    val record = createMiroRecord
    val location = createS3ObjectLocation

    store.put(location)(record) shouldBe a[Right[_, _]]

    MiroSourcePayload(
      id = record.imageNumber,
      version = 1,
      location = location,
      isClearedForCatalogueAPI = chooseFrom(true, false)
    )
  }

  override def createBadPayload(
    implicit context: MemoryTypedStore[S3ObjectLocation, MiroRecord])
    : MiroSourcePayload =
    MiroSourcePayload(
      id = randomAlphanumeric(),
      version = 1,
      location = createS3ObjectLocation,
      isClearedForCatalogueAPI = chooseFrom(true, false)
    )

  override implicit val encoder: Encoder[MiroSourcePayload] =
    deriveConfiguredEncoder[MiroSourcePayload]

  override def assertMatches(p: MiroSourcePayload, w: Work[WorkState.Source])(
    implicit source: MemoryTypedStore[S3ObjectLocation, MiroRecord]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType(
      "miro-image-number")
    p.id shouldBe w.sourceIdentifier.value
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String])(
    testWith: TestWith[
      TransformerWorker[MiroSourcePayload, (MiroRecord, MiroMetadata), String],
      R])(
    implicit miroReadable: MemoryTypedStore[S3ObjectLocation, MiroRecord]): R =
    testWith(
      new MiroTransformerWorker(
        pipelineStream = pipelineStream,
        miroReadable = miroReadable
      )
    )
}
