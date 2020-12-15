package uk.ac.wellcome.platform.transformer.sierra.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{IdentifierType, Work, WorkState}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraGenerators, SierraTransformable}
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.transformer.{TransformerWorker, TransformerWorkerTestCases}

class SierraTransformerWorkerTest
  extends TransformerWorkerTestCases[MemoryTypedStore[S3ObjectLocation, SierraTransformable], SierraSourcePayload, SierraTransformable]
    with SierraGenerators
    with S3ObjectLocationGenerators {

  override def withContext[R](testWith: TestWith[MemoryTypedStore[S3ObjectLocation, SierraTransformable], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, SierraTransformable](initialEntries = Map.empty)
    )

  override def createPayload(implicit store: MemoryTypedStore[S3ObjectLocation, SierraTransformable]): SierraSourcePayload = {
    val transformable = createSierraTransformable
    val location = createS3ObjectLocation

    store.put(location)(transformable) shouldBe a[Right[_, _]]

    SierraSourcePayload(
      id = transformable.sierraId.withoutCheckDigit,
      location = location,
      version = 1
    )
  }

  override def createBadPayload(implicit context: MemoryTypedStore[S3ObjectLocation, SierraTransformable]): SierraSourcePayload =
    SierraSourcePayload(
      id = createSierraBibNumber.withoutCheckDigit,
      location = createS3ObjectLocation,
      version = 1
    )

  override implicit val encoder: Encoder[SierraSourcePayload] =
    deriveConfiguredEncoder[SierraSourcePayload]

  override def assertMatches(p: SierraSourcePayload, w: Work[WorkState.Source])(implicit context: MemoryTypedStore[S3ObjectLocation, SierraTransformable]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType("sierra-system-number")
    w.sourceIdentifier.value shouldBe SierraBibNumber(p.id).withCheckDigit
  }

  override def withWorker[R](pipelineStream: PipelineStorageStream[NotificationMessage, Work[WorkState.Source], String])(testWith: TestWith[TransformerWorker[SierraSourcePayload, SierraTransformable, String], R])(implicit sierraReadable: MemoryTypedStore[S3ObjectLocation, SierraTransformable]): R =
    testWith(
      new SierraTransformerWorker(
        pipelineStream = pipelineStream,
        sierraReadable = sierraReadable
      )
    )
}
