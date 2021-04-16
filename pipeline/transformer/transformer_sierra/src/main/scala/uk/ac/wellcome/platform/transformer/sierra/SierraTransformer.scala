package uk.ac.wellcome.platform.transformer.sierra

import java.time.Instant
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.platform.transformer.sierra.exceptions._
import uk.ac.wellcome.platform.transformer.sierra.source._
import uk.ac.wellcome.platform.transformer.sierra.source.SierraMaterialType._
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData._
import uk.ac.wellcome.platform.transformer.sierra.transformers._
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.work.DeletedReason._
import weco.catalogue.internal_model.work.InvisibilityReason._
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.catalogue.source_model.sierra._

class SierraTransformer(sierraTransformable: SierraTransformable, version: Int)
    extends Logging {

  def transform: Try[Work[Source]] =
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
          Work.Invisible[Source](
            state = Source(sourceIdentifier, Instant.EPOCH),
            version = version,
            data = WorkData(),
            invisibilityReasons = List(SourceFieldMissing("bibData"))
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
            s"Failed to perform transform to unified item of $sourceIdentifier",
            e)
          throw e
      }

  def workFromBibRecord(bibRecord: SierraBibRecord): Try[Work[Source]] = {
    val state = Source(sourceIdentifier, bibRecord.modifiedDate)

    fromJson[SierraBibData](bibRecord.data)
      .map { bibData =>
        if (bibData.deleted) {
          Work.Deleted[Source](
            version = version,
            state = state,
            deletedReason = DeletedFromSource("Sierra"),
            data = WorkData()
          )
        } else if (bibData.suppressed) {
          Work.Deleted[Source](
            version = version,
            state = state,
            deletedReason = SuppressedFromSource("Sierra"),
            data = WorkData()
          )
        } else {
          Work.Visible[Source](
            version = version,
            state = state,
            data = workDataFromBibData(bibId, bibData)
          )
        }
      }
      .recover {
        case e: JsonDecodingError =>
          throw SierraTransformerException(
            s"Unable to parse bib data for ${bibRecord.id} as JSON: <<${bibRecord.data}>> ($e)"
          )
        case e: ShouldNotTransformException =>
          debug(s"Should not transform $bibId: ${e.getMessage}")
          Work.Invisible[Source](
            state = state,
            version = version,
            data = WorkData(),
            invisibilityReasons = List(UnableToTransform(e.getMessage))
          )
      }
  }

  def workDataFromBibData(bibId: SierraBibNumber, bibData: SierraBibData) =
    WorkData[DataState.Unidentified](
      otherIdentifiers = SierraIdentifiers(bibId, bibData),
      mergeCandidates = SierraMergeCandidates(bibId, bibData),
      title = SierraTitle(bibData),
      alternativeTitles = SierraAlternativeTitles(bibData),
      format = SierraFormat(bibData),
      description = SierraDescription(bibId, bibData),
      physicalDescription = SierraPhysicalDescription(bibData),
      lettering = SierraLettering(bibData),
      subjects = SierraSubjects(bibId, bibData),
      genres = SierraGenres(bibData),
      contributors = SierraContributors(bibData),
      production = SierraProduction(bibId, bibData),
      languages = SierraLanguages(bibId, bibData),
      edition = SierraEdition(bibData),
      notes = SierraNotes(bibData),
      duration = SierraDuration(bibData),
      items = SierraItems(itemDataMap)(bibId, bibData) ++
        SierraElectronicResources(bibId, varFields = bibData.varFields),
      holdings = SierraHoldings(bibId, holdingsDataMap)
    )

  lazy val bibId = sierraTransformable.sierraId

  lazy val sourceIdentifier = SourceIdentifier(
    identifierType = IdentifierType.SierraSystemNumber,
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

  lazy val holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData] =
    sierraTransformable.holdingsRecords
      .map { case (id, hRecord) => (id, hRecord.data) }
      .map {
        case (id, jsonString) =>
          fromJson[SierraHoldingsData](jsonString) match {
            case Success(data) => id -> data
            case Failure(_) =>
              throw SierraTransformerException(
                s"Unable to parse holdings data for $id as JSON: <<$jsonString>>")
          }
      }
}

object SierraTransformer {

  def apply(transformable: SierraTransformable,
            version: Int): Try[Work[Source]] =
    new SierraTransformer(transformable, version).transform
}
