package weco.pipeline.transformer.sierra

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.work.DeletedReason._
import weco.catalogue.internal_model.work.InvisibilityReason._
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{
  Contributor,
  Relations,
  Subject,
  Work,
  WorkData
}
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.Implicits._
import weco.json.JsonUtil.fromJson
import weco.json.exceptions.JsonDecodingError
import weco.pipeline.transformer.sierra.exceptions.{
  ShouldNotTransformException,
  SierraTransformerException
}
import weco.pipeline.transformer.sierra.transformers._
import weco.sierra.models.data.{
  SierraBibData,
  SierraHoldingsData,
  SierraItemData,
  SierraOrderData
}
import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraOrderNumber
}

import java.time.Instant
import scala.util.{Failure, Success, Try}

class SierraTransformer(sierraTransformable: SierraTransformable, version: Int)
    extends Logging {

  def transform: Try[Work[Source]] =
    sierraTransformable.maybeBibRecord
      .map {
        bibRecord =>
          debug(s"Attempting to transform ${bibRecord.id.withCheckDigit}")

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
            invisibilityReasons = List(SourceFieldMissing("bibRecord"))
          )
        )
      }
      .map {
        transformed =>
          debug(s"Transformed record to $transformed")
          transformed
      }
      .recover {
        case e: Throwable =>
          error(
            s"Failed to perform transform to unified item of $sourceIdentifier",
            e
          )
          throw e
      }

  def workFromBibRecord(bibRecord: SierraBibRecord): Try[Work[Source]] =
    fromJson[SierraBibData](bibRecord.data)
      .map {
        bibData =>
          val state = Source(
            sourceIdentifier = sourceIdentifier,
            sourceModifiedTime = sierraTransformable.modifiedTime,
            mergeCandidates = SierraMergeCandidates(bibId, bibData),
            relations = Relations(ancestors = SierraParents(bibData))
          )

          if (bibData.deleted) {
            Work.Deleted[Source](
              version = version,
              state = state,
              deletedReason = DeletedFromSource("Sierra")
            )
          } else if (bibData.suppressed) {
            Work.Deleted[Source](
              version = version,
              state = state,
              deletedReason = SuppressedFromSource("Sierra")
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

          val state = Source(
            sourceIdentifier = sourceIdentifier,
            sourceModifiedTime = sierraTransformable.modifiedTime
          )

          Work.Invisible[Source](
            state = state,
            version = version,
            data = WorkData(),
            invisibilityReasons = List(UnableToTransform(e.getMessage))
          )
      }

  def workDataFromBibData(bibId: SierraBibNumber, bibData: SierraBibData) = {
    val (
      subjects: List[Subject[IdState.Unminted]],
      contributors: List[Contributor[IdState.Unminted]]
    ) = SubjectsAndContributors(bibId, bibData)
    WorkData[DataState.Unidentified](
      otherIdentifiers = SierraIdentifiers(bibId, bibData),
      title = SierraTitle(bibData),
      alternativeTitles = SierraAlternativeTitles(bibData),
      format = SierraFormat(bibData),
      description = SierraDescription(bibId, bibData),
      physicalDescription = SierraPhysicalDescription(bibData),
      lettering = SierraLettering(bibData),
      subjects = subjects,
      genres = SierraGenres(bibData),
      contributors = contributors,
      production = SierraProduction(bibId, bibData),
      languages = SierraLanguages(bibId, bibData),
      edition = SierraEdition(bibData),
      notes = SierraNotes(bibData),
      duration = SierraDuration(bibData),
      items = SierraItemsOnOrder(
        bibId,
        bibData = bibData,
        hasItems = hasItems,
        orderDataMap
      ) ++
        SierraItems(bibId, bibData, itemDataEntries) ++
        SierraElectronicResources(bibId, varFields = bibData.varFields),
      holdings = SierraHoldings(bibId, holdingsDataMap),
      referenceNumber = SierraReferenceNumber(bibData),
      collectionPath = SierraCollectionPath(bibData),
      currentFrequency = SierraCurrentFrequency(bibData),
      formerFrequency = SierraFormerFrequency(bibData),
      designation = SierraDesignation(bibData)
    )
  }

  lazy val bibId = sierraTransformable.sierraId

  lazy val sourceIdentifier = SourceIdentifier(
    identifierType = IdentifierType.SierraSystemNumber,
    ontologyType = "Work",
    value = bibId.withCheckDigit
  )

  lazy val hasItems: Boolean =
    sierraTransformable.itemRecords.nonEmpty

  lazy val itemDataEntries: Seq[SierraItemData] =
    sierraTransformable.itemRecords.map {
      case (_, itemRecord) =>
        fromJson[SierraItemData](itemRecord.data) match {
          case Success(itemData) => itemData
          case Failure(_) =>
            throw SierraTransformerException(
              s"Unable to parse item data for ${itemRecord.id} as JSON: <<${itemRecord.data}>>"
            )
        }
    }.toSeq

  lazy val holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData] =
    sierraTransformable.holdingsRecords
      .map { case (id, hRecord) => (id, hRecord.data) }
      .map {
        case (id, jsonString) =>
          fromJson[SierraHoldingsData](jsonString) match {
            case Success(data) => id -> data
            case Failure(_) =>
              throw SierraTransformerException(
                s"Unable to parse holdings data for $id as JSON: <<$jsonString>>"
              )
          }
      }

  lazy val orderDataMap: Map[SierraOrderNumber, SierraOrderData] =
    sierraTransformable.orderRecords
      .map { case (id, oRecord) => (id, oRecord.data) }
      .map {
        case (id, jsonString) =>
          fromJson[SierraOrderData](jsonString) match {
            case Success(data) => id -> data
            case Failure(_) =>
              throw SierraTransformerException(
                s"Unable to parse order data for $id as JSON: <<$jsonString>>"
              )
          }
      }
}

object SierraTransformer {

  def apply(
    transformable: SierraTransformable,
    version: Int
  ): Try[Work[Source]] =
    new SierraTransformer(transformable, version).transform
}
