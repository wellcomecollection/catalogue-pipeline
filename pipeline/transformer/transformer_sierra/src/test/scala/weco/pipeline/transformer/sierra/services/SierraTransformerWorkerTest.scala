package weco.pipeline.transformer.sierra.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.EitherValues
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.generators.SierraGenerators
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{TransformerWorker, TransformerWorkerTestCases}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.sierra.models.identifiers.SierraBibNumber
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import scala.concurrent.ExecutionContext.Implicits.global

class SierraTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, SierraTransformable],
      SierraSourcePayload,
      SierraTransformable]
    with SierraGenerators
    with S3ObjectLocationGenerators
    with EitherValues {

  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, SierraTransformable],
                       R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, SierraTransformable](
        initialEntries = Map.empty)
    )

  override def createId: String = createSierraBibNumber.withoutCheckDigit

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, SierraTransformable])
    : SierraSourcePayload = {
    val transformable = createSierraTransformableWith(
      sierraId = SierraBibNumber(id))
    val location = createS3ObjectLocation

    store.put(location)(transformable) shouldBe a[Right[_, _]]

    SierraSourcePayload(
      id = transformable.sierraId.withoutCheckDigit,
      location = location,
      version = version
    )
  }

  override def setPayloadVersion(p: SierraSourcePayload, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, SierraTransformable])
    : SierraSourcePayload = {
    val transformable: SierraTransformable =
      store.get(p.location).value.identifiedT

    val location = createS3ObjectLocation
    store.put(location)(transformable) shouldBe a[Right[_, _]]

    p.copy(location = location, version = version)
  }

  override def createBadPayload(
    implicit context: MemoryTypedStore[S3ObjectLocation, SierraTransformable])
    : SierraSourcePayload =
    SierraSourcePayload(
      id = createSierraBibNumber.withoutCheckDigit,
      location = createS3ObjectLocation,
      version = 1
    )

  override implicit val encoder: Encoder[SierraSourcePayload] =
    deriveConfiguredEncoder[SierraSourcePayload]

  override def assertMatches(p: SierraSourcePayload, w: Work[WorkState.Source])(
    implicit context: MemoryTypedStore[S3ObjectLocation, SierraTransformable])
    : Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType.SierraSystemNumber
    w.sourceIdentifier.value shouldBe SierraBibNumber(p.id).withCheckDigit
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String],
    retriever: Retriever[Work[WorkState.Source]])(
    testWith: TestWith[
      TransformerWorker[SierraSourcePayload, SierraTransformable, String],
      R])(implicit sierraReadable: MemoryTypedStore[S3ObjectLocation,
                                                    SierraTransformable]): R =
    testWith(
      new SierraTransformerWorker(
        pipelineStream = pipelineStream,
        sierraReadable = sierraReadable,
        retriever = retriever
      )
    )
}
