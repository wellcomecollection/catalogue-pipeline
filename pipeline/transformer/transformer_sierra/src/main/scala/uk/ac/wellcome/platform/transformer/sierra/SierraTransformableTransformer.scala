package uk.ac.wellcome.platform.transformer.sierra

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.{
  SierraBibRecord,
  SierraItemNumber
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.{
  ShouldNotTransformException,
  SierraTransformerException
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData
}
import uk.ac.wellcome.platform.transformer.sierra.transformers._
import uk.ac.wellcome.platform.transformer.sierra.source.SierraMaterialType._

import grizzled.slf4j.Logging
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
            version = version
          )
        )
      }
      .map { transformed =>
        debug(s"Transformed record to $transformed")
        transformed
      }
      .recover {
        case e: Throwable =>
          error("Failed to perform transform to unified item", e)
          throw e
      }

  def workFromBibRecord(bibRecord: SierraBibRecord): Try[TransformedBaseWork] =
    fromJson[SierraBibData](bibRecord.data)
      .map { bibData =>
        if (bibData.deleted || bibData.suppressed) {
          throw new ShouldNotTransformException(
            s"Sierra record $bibId is either deleted or suppressed!"
          )
        }
        UnidentifiedWork(
          sourceIdentifier = sourceIdentifier,
          otherIdentifiers = SierraIdentifiers(bibId, bibData),
          mergeCandidates = SierraMergeCandidates(bibId, bibData),
          title = SierraTitle(bibId, bibData),
          alternativeTitles = SierraAlternativeTitles(bibId, bibData),
          workType = SierraWorkType(bibId, bibData),
          description = SierraDescription(bibId, bibData),
          physicalDescription = SierraPhysicalDescription(bibId, bibData),
          extent = SierraExtent(bibId, bibData),
          lettering = SierraLettering(bibId, bibData),
          createdDate = None,
          subjects = SierraSubjects(bibId, bibData),
          genres = SierraGenres(bibId, bibData),
          contributors = SierraContributors(bibId, bibData),
          thumbnail = None,
          production = SierraProduction(bibId, bibData),
          language = SierraLanguage(bibId, bibData),
          dimensions = SierraDimensions(bibId, bibData),
          edition = SierraEdition(bibId, bibData),
          notes = SierraNotes(bibId, bibData),
          items = SierraItems(itemDataMap)(bibId, bibData),
          itemsV1 = Nil,
          version = version
        )
      }
      .recover {
        case e: JsonDecodingError =>
          throw SierraTransformerException(
            s"Unable to parse bib data for ${bibRecord.id} as JSON: <<${bibRecord.data}>>"
          )
        case e: ShouldNotTransformException =>
          debug(s"Should not transform $bibId: ${e.getMessage}")
          UnidentifiedInvisibleWork(
            sourceIdentifier = sourceIdentifier,
            version = version
          )
      }

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
