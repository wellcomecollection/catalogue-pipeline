package uk.ac.wellcome.platform.transformer.miro

import java.time.Instant
import scala.util.{Success, Try}
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.exceptions.{
  ShouldNotTransformException,
  ShouldSuppressException
}
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.platform.transformer.miro.transformers._
import WorkState.Source
import uk.ac.wellcome.models.work.internal.DeletedReason.SuppressedFromSource
import uk.ac.wellcome.models.work.internal.InvisibilityReason.UnableToTransform
import uk.ac.wellcome.models.work.internal.result.Result
import weco.catalogue.transformer.Transformer

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
    with Transformer[(MiroRecord, MiroMetadata)] {

  override def apply(sourceData: (MiroRecord, MiroMetadata),
                     version: Int): Result[Work[Source]] = {
    val (miroRecord, miroMetadata) = sourceData

    transform(miroRecord, miroMetadata, version).toEither
  }

  def transform(miroRecord: MiroRecord,
                miroMetadata: MiroMetadata,
                version: Int): Try[Work[Source]] =
    doTransform(miroRecord, miroMetadata, version) map { transformed =>
      debug(s"Transformed record to $transformed")
      transformed
    } recover {
      case e: Throwable =>
        error("Failed to perform transform to unified item", e)
        throw e
    }

  private def doTransform(originalMiroRecord: MiroRecord,
                          miroMetadata: MiroMetadata,
                          version: Int): Try[Work[Source]] = {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("miro-image-number"),
      ontologyType = "Work",
      value = originalMiroRecord.imageNumber
    )

    val state = Source(
      sourceIdentifier = sourceIdentifier,
      // Miro records are static so we just send 0 as a last modification timestamp
      modifiedTime = Instant.EPOCH
    )

    if (!miroMetadata.isClearedForCatalogueAPI) {
      Success(
        Work.Deleted[Source](
          version = version,
          data = WorkData(),
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
          data = WorkData(),
          state = state,
          deletedReason = SuppressedFromSource(
            s"Miro: image_copyright_cleared = ${originalMiroRecord.copyrightCleared
              .getOrElse("<empty>")}")
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
          thumbnail = Some(getThumbnail(miroRecord)),
          items = getItems(miroRecord),
          imageData = List(getImageData(miroRecord, version = version))
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
              data = WorkData(),
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
