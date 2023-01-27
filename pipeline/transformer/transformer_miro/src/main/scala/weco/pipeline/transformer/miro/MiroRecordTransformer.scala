package weco.pipeline.transformer.miro

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  DataState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.DeletedReason.SuppressedFromSource
import weco.catalogue.internal_model.work.InvisibilityReason.UnableToTransform
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.json.JsonUtil.toJson
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.miro.exceptions.{
  ShouldNotTransformException,
  ShouldSuppressException
}
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.pipeline.transformer.miro.transformers._
import weco.pipeline.transformer.result.Result
import Implicits._

import java.time.Instant
import scala.util.{Success, Try}

class MiroRecordTransformer
    extends MiroContributors
    with MiroCreatedDate
    with MiroItems
    with MiroGenres
    with MiroImageData
    with MiroIdentifiers
    with MiroSubjects
    with MiroThumbnail
    with MiroTitleAndDescription
    with MiroFormat
    with Logging
    with Transformer[(MiroRecord, MiroSourceOverrides, MiroMetadata)] {

  override def apply(
    id: String,
    sourceData: (MiroRecord, MiroSourceOverrides, MiroMetadata),
    version: Int
  ): Result[Work[Source]] = {
    val (miroRecord, overrides, miroMetadata) = sourceData

    transform(miroRecord, overrides, miroMetadata, version).toEither
  }

  def transform(
    miroRecord: MiroRecord,
    overrides: MiroSourceOverrides,
    miroMetadata: MiroMetadata,
    version: Int
  ): Try[Work[Source]] =
    doTransform(miroRecord, overrides, miroMetadata, version) map {
      transformed =>
        debug(s"Transformed record to $transformed")
        transformed
    } recover { case e: Throwable =>
      error("Failed to perform transform to unified item", e)
      throw e
    }

  private def doTransform(
    originalMiroRecord: MiroRecord,
    overrides: MiroSourceOverrides,
    miroMetadata: MiroMetadata,
    version: Int
  ): Try[Work[Source]] = {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MiroImageNumber,
      ontologyType = "Work",
      value = originalMiroRecord.imageNumber
    )

    val state = Source(
      sourceIdentifier = sourceIdentifier,
      // Miro records are static so we just send 0 as a last modification timestamp
      sourceModifiedTime = Instant.EPOCH
    )

    if (!miroMetadata.isClearedForCatalogueAPI) {
      Success(
        Work.Deleted[Source](
          version = version,
          state = state,
          deletedReason =
            SuppressedFromSource("Miro: isClearedForCatalogueAPI = false")
        )
      )
    }
    // These images should really have been removed from the pipeline
    // already, but we have at least one instance (B0010525).  It was
    // throwing a MatchError when we tried to pick a license, so handle
    // it properly here.
    else if (!originalMiroRecord.copyrightCleared.contains("Y")) {
      Success(
        Work.Deleted[Source](
          version = version,
          state = state,
          deletedReason = SuppressedFromSource(
            s"Miro: image_copyright_cleared = ${originalMiroRecord.copyrightCleared
                .getOrElse("<empty>")}"
          )
        )
      )
    } else {
      Try {
        // This is an utterly awful hack we have to live with until we get
        // these corrected in the source data.
        val miroRecord = MiroRecord.create(toJson(originalMiroRecord).get)

        val (title, description) = getTitleAndDescription(miroRecord)

        val data = WorkData[DataState.Unidentified](
          otherIdentifiers = getOtherIdentifiers(miroRecord),
          title = Some(title),
          format = getFormat,
          description = description,
          lettering = miroRecord.suppLettering,
          createdDate = getCreatedDate(miroRecord),
          subjects = getSubjects(miroRecord),
          genres = getGenres(miroRecord),
          contributors = getContributors(miroRecord),
          thumbnail = Some(getThumbnail(miroRecord, overrides)),
          items = getItems(miroRecord, overrides),
          imageData = List(
            getImageData(miroRecord, overrides = overrides, version = version)
          )
        )

        Work.Visible[Source](
          version = version,
          state = state,
          data = data
        )
      }.recover {
        case e: ShouldSuppressException =>
          Work.Deleted[Source](
            version = version,
            state = state,
            deletedReason = SuppressedFromSource(s"Miro: ${e.getMessage}")
          )

        case e: ShouldNotTransformException =>
          debug(s"Should not transform: ${e.getMessage}")
          Work.Invisible[Source](
            state = state,
            version = version,
            data = WorkData(),
            invisibilityReasons = List(
              UnableToTransform(s"Miro: ${e.getMessage}")
            )
          )
      }
    }
  }
}
