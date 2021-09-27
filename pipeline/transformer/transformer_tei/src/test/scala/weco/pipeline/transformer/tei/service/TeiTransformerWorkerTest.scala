package weco.pipeline.transformer.tei.service

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  CollectionPath,
  Format,
  MergeCandidate,
  Work,
  WorkData,
  WorkState
}
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiMetadata}
import weco.fixtures.TestWith
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{TransformerWorker, TransformerWorkerTestCases}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore
import weco.json.JsonUtil._
import weco.pipeline.transformer.tei.TeiTransformer
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class TeiTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, String],
      TeiSourcePayload,
      TeiMetadata
    ]
    with TeiGenerators
    with S3ObjectLocationGenerators {
  val description = "This is a summary"
  val bnumber = "b12345672"
  val title = "This is the title"

  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, String], R]
  ): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, String]()
    )

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, String]
  ): TeiSourcePayload = {
    val xmlString =
      teiXml(
        id,
        title = titleElem(title),
        identifiers = Some(sierraIdentifiers(bnumber)),
        summary = Some(summary(description)))
        .toString()

    val location = S3ObjectLocation(
      bucket = createBucketName,
      key = s"tei_files/$id/Tei.xml"
    )

    store.put(location)(xmlString) shouldBe a[Right[_, _]]
    TeiSourcePayload(id, TeiChangedMetadata(location, Instant.now()), version)
  }

  override def setPayloadVersion(p: TeiSourcePayload, version: Int)(
    implicit context: MemoryTypedStore[S3ObjectLocation, String]
  ): TeiSourcePayload = p.copy(version = version)

  override def createBadPayload(
    implicit context: MemoryTypedStore[S3ObjectLocation, String]
  ): TeiSourcePayload =
    TeiSourcePayload(
      "whatever",
      TeiChangedMetadata(createS3ObjectLocation, Instant.now()),
      1
    )

  override implicit val encoder: Encoder[TeiSourcePayload] =
    deriveConfiguredEncoder[TeiSourcePayload]

  override def assertMatches(p: TeiSourcePayload, w: Work[WorkState.Source])(
    implicit context: MemoryTypedStore[S3ObjectLocation, String]
  ): Unit = {
    w.sourceIdentifier shouldBe SourceIdentifier(
      IdentifierType.Tei,
      "Work",
      p.id
    )
    w.version shouldBe p.version
    w.state.mergeCandidates shouldBe List(
      MergeCandidate(
        SourceIdentifier(IdentifierType.SierraSystemNumber, "Work", bnumber),
        "Bnumber present in TEI file"
      )
    )
    w.data shouldBe WorkData[Unidentified](
      title = Some(title),
      description = Some(description),
      format = Some(Format.ArchivesAndManuscripts),
      collectionPath = Some(CollectionPath(path = p.id, label = None))
    )
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[
                                            WorkState.Source
                                          ],
                                          String],
    retriever: Retriever[Work[WorkState.Source]]
  )(
    testWith: TestWith[
      TransformerWorker[TeiSourcePayload, TeiMetadata, String],
      R
    ]
  )(implicit context: MemoryTypedStore[S3ObjectLocation, String]): R = {
    val transformerWorker = new TeiTransformerWorker[String](
      new TeiTransformer(context),
      retriever,
      pipelineStream
    )
    testWith(transformerWorker)
  }
}
