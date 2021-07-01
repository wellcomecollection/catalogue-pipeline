package uk.ac.wellcome.platform.transformer.tei.transformer.service

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.tei.transformer.TeiTransformer
import uk.ac.wellcome.platform.transformer.tei.transformer.fixtures.TeiGenerators
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkData, WorkState}
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiMetadata}
import weco.catalogue.transformer.{TransformerWorker, TransformerWorkerTestCases}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global


class TeiTransformerWorkerTest extends TransformerWorkerTestCases[MemoryTypedStore[S3ObjectLocation, String],TeiSourcePayload, TeiMetadata] with TeiGenerators  with S3ObjectLocationGenerators{
  val description = "This is a summary"
  val bnumber = "b1234567"

  override def withContext[R](testWith: TestWith[MemoryTypedStore[S3ObjectLocation, String], R]): R = testWith(
    MemoryTypedStore[S3ObjectLocation, String]()
  )

  override def createPayloadWith(id: String, version: Int)(implicit store: MemoryTypedStore[S3ObjectLocation, String]): TeiSourcePayload = {
    val xmlString = teiXml(id, Some(sierraIdentifiers(bnumber)), Some(summary(description))).toString()

    val location = S3ObjectLocation(
      bucket = createBucketName,
      key = s"tei_files/$id/METS.xml"
    )

    store.put(location)(xmlString) shouldBe a[Right[_, _]]
    TeiSourcePayload(id, TeiChangedMetadata(location, Instant.now()),version)
  }

  override def setPayloadVersion(p: TeiSourcePayload, version: Int)(implicit context: MemoryTypedStore[S3ObjectLocation, String]): TeiSourcePayload = p.copy(version = version)

  override def createBadPayload(implicit context: MemoryTypedStore[S3ObjectLocation, String]): TeiSourcePayload =
    TeiSourcePayload("whatever", TeiChangedMetadata(createS3ObjectLocation, Instant.now()),1)

  override implicit val encoder: Encoder[TeiSourcePayload] = deriveConfiguredEncoder[TeiSourcePayload]

  override def assertMatches(p: TeiSourcePayload, w: Work[WorkState.Source])(implicit context: MemoryTypedStore[S3ObjectLocation, String]): Unit = {
    w.sourceIdentifier shouldBe SourceIdentifier(IdentifierType.Tei, "Work", p.id)
    w.version shouldBe p.version
    w.data shouldBe WorkData[Unidentified](description = Some(description), mergeCandidates = List(MergeCandidate(SourceIdentifier(IdentifierType.SierraSystemNumber, "Work", bnumber), "Bnumber present in TEI file")))
  }

  override def withWorker[R](pipelineStream: PipelineStorageStream[NotificationMessage, Work[WorkState.Source], String], retriever: Retriever[Work[WorkState.Source]])(testWith: TestWith[TransformerWorker[TeiSourcePayload, TeiMetadata, String], R])(implicit context: MemoryTypedStore[S3ObjectLocation, String]): R = {
    val transformerWorker= new TeiTransformerWorker[String](new TeiTransformer(context),retriever,pipelineStream)
    testWith(transformerWorker)
  }
}
