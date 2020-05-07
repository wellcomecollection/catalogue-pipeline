package uk.ac.wellcome.platform.transformer.sierra

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.{SierraBibNumber, SierraBibRecord, SierraItemNumber}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.{RecordDeletedException, RecordSuppressedException, SierraTransformerException, TitleMissingException}
import uk.ac.wellcome.platform.transformer.sierra.source.{SierraBibData, SierraItemData}
import uk.ac.wellcome.platform.transformer.sierra.transformers._
import uk.ac.wellcome.platform.transformer.sierra.source.SierraMaterialType._
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.InvisibilityReason.{SierraDeleted, SierraSuppressed, SierraTitleMissing}

import scala.util.{Failure, Success, Try}

object SierraTransformableTransformer {

  def apply(transformable: SierraTransformable,
            version: Int): Try[TransformedBaseWork] =
    new SierraTransformableTransformer(transformable, version).transform
}

class SierraTransformableTransformer(sierraTransformable: SierraTransformable,
                                     version: Int)
    extends Logging {

  def transform: Try[TransformedBaseWork] =
    sierraTransformable.maybeBibRecord
      .map { bibRecord =>
        debug(s"Attempting to transform ${bibRecord.id}")
        workFromBibRecord(bibRecord)
      }
      .getOrElse {
        // A merged record can have both bibs and items.  If we only have
        // the item data so far, we don't have enough to build a Work to show
        // in the API, so we return an InvisibleWork.
        debug(s"No bib data for ${sierraTransformable.sierraId}, so skipping")
        Success(
          UnidentifiedInvisibleWork(
            sourceIdentifier = sourceIdentifier,
            version = version,
            data = WorkData(),
            reasons = List()
          )
        )
      }
      .map { transformed =>
        debug(s"Transformed record to $transformed")
        transformed
      }
      .recover {
        case e: Throwable =>
          error(
            s"Failed to perform transform to unified item of ${sourceIdentifier}",
            e)
          throw e
      }

  def workFromBibRecord(bibRecord: SierraBibRecord): Try[TransformedBaseWork] =
    fromJson[SierraBibData](bibRecord.data)
      .map { bibData =>
        if (bibData.deleted) {
          throw new RecordDeletedException(
            s"Sierra record $bibId is deleted!"
          )
        }
        if (bibData.suppressed) {
          throw new RecordSuppressedException(
            s"Sierra record $bibId is suppressed!"
          )
        }
        val data = workDataFromBibData(bibId, bibData)
        UnidentifiedWork(version, sourceIdentifier, data)
      }
      .recover {
        case e: JsonDecodingError =>
          throw SierraTransformerException(
            s"Unable to parse bib data for ${bibRecord.id} as JSON: <<${bibRecord.data}>>"
          )
        case e: RecordDeletedException =>
          debug(s"Should not transform $bibId: ${e.getMessage}")
          UnidentifiedInvisibleWork(
            sourceIdentifier = sourceIdentifier,
            version = version,
            data = WorkData(),
            reasons = List(SierraDeleted)
          )
        case e: RecordSuppressedException =>
          debug(s"Should not transform $bibId: ${e.getMessage}")
          UnidentifiedInvisibleWork(
            sourceIdentifier = sourceIdentifier,
            version = version,
            data = WorkData(),
            reasons = List(SierraSuppressed)
          )

        case e: TitleMissingException =>
          debug(s"Should not transform $bibId: ${e.getMessage}")
          UnidentifiedInvisibleWork(
            sourceIdentifier = sourceIdentifier,
            version = version,
            data = WorkData(),
            reasons = List(SierraTitleMissing)
          )
      }

  def workDataFromBibData(bibId: SierraBibNumber, bibData: SierraBibData) =
    WorkData[Unminted, Identifiable](
      otherIdentifiers = SierraIdentifiers(bibId, bibData),
      mergeCandidates = SierraMergeCandidates(bibId, bibData),
      title = SierraTitle(bibId, bibData),
      alternativeTitles = SierraAlternativeTitles(bibId, bibData),
      workType = SierraWorkType(bibId, bibData),
      description = SierraDescription(bibId, bibData),
      physicalDescription = SierraPhysicalDescription(bibId, bibData),
      lettering = SierraLettering(bibId, bibData),
      subjects = SierraSubjects(bibId, bibData),
      genres = SierraGenres(bibId, bibData),
      contributors = SierraContributors(bibId, bibData),
      production = SierraProduction(bibId, bibData),
      language = SierraLanguage(bibId, bibData),
      edition = SierraEdition(bibId, bibData),
      notes = SierraNotes(bibId, bibData),
      duration = SierraDuration(bibId, bibData),
      items = SierraItems(itemDataMap)(bibId, bibData)
    )

  lazy val bibId = sierraTransformable.sierraId

  lazy val sourceIdentifier = SourceIdentifier(
    identifierType = IdentifierType("sierra-system-number"),
    ontologyType = "Work",
    value = bibId.withCheckDigit
  )

  lazy val itemDataMap: Map[SierraItemNumber, SierraItemData] =
    sierraTransformable.itemRecords
      .map { case (id, itemRecord) => (id, itemRecord.data) }
      .map {
        case (id, jsonString) =>
          fromJson[SierraItemData](jsonString) match {
            case Success(data) => id -> data
            case Failure(_) =>
              throw SierraTransformerException(
                s"Unable to parse item data for $id as JSON: <<$jsonString>>")
          }
      }
}
