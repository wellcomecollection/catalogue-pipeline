package uk.ac.wellcome.platform.transformer.calm.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.EitherValues
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{IdentifierType, Work, WorkState}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.calm.CalmRecord
import uk.ac.wellcome.platform.transformer.calm.generators.CalmRecordGenerators
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.transformer.{
  TransformerWorker,
  TransformerWorkerTestCases
}

import java.util.UUID

class CalmTransformerWorkerTest
  extends TransformerWorkerTestCases[MemoryTypedStore[S3ObjectLocation, CalmRecord], CalmSourcePayload, CalmRecord]
    with EitherValues
    with CalmRecordGenerators
    with S3ObjectLocationGenerators {

  override def withContext[R](testWith: TestWith[MemoryTypedStore[S3ObjectLocation, CalmRecord], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, CalmRecord](initialEntries = Map.empty)
    )

  override def createPayload(implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord]): CalmSourcePayload = {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )

    val location = createS3ObjectLocation

    val id = UUID.randomUUID().toString

    store.put(location)(record.copy(id = id)) shouldBe a[Right[_, _]]

    CalmSourcePayload(id = id, location = location, version = 1)
  }

  override def createBadPayload(implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord]): CalmSourcePayload =
    CalmSourcePayload(id = UUID.randomUUID().toString, location = createS3ObjectLocation, version = 1)

  override implicit val encoder: Encoder[CalmSourcePayload] =
    deriveConfiguredEncoder[CalmSourcePayload]

  override def assertMatches(p: CalmSourcePayload, w: Work[WorkState.Source])(implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType("calm-record-id")
    w.sourceIdentifier.value shouldBe p.id
  }

  override def withWorker[R](pipelineStream: PipelineStorageStream[NotificationMessage, Work[WorkState.Source], String])(testWith: TestWith[TransformerWorker[CalmSourcePayload, CalmRecord, String], R])(implicit recordReadable: MemoryTypedStore[S3ObjectLocation, CalmRecord]): R =
    testWith(
      new CalmTransformerWorker(
        pipelineStream = pipelineStream,
        recordReadable = recordReadable
      )
    )
}
