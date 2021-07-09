package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{
  LocationType,
  PhysicalLocation,
  PhysicalLocationType
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.rules.{
  SierraAccessStatus,
  SierraItemAccess,
  SierraPhysicalLocationType
}
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.identifiers.{
  SierraBibNumber,
  SierraItemNumber
}
import weco.catalogue.source_model.sierra.marc.VarField
import weco.catalogue.source_model.sierra.{SierraBibData, SierraItemData}
import weco.pipeline.transformer.sierra.data.SierraPhysicalItemOrder

object SierraItems extends Logging with SierraLocation with SierraQueryOps {

  type Output = List[Item[IdState.Unminted]]

  type HasInferredTitle = Boolean

  /** We don't get the digital items from Sierra.
    * The `dlnk` was previously used, but we now use the METS source.
    *
    * So the output is deterministic here we sort all items by the
    * sierra-identifier.  We want to revisit this at some point.
    * See https://github.com/wellcomecollection/platform/issues/4993
    */
  def apply(bibId: SierraBibNumber,
            bibData: SierraBibData,
            itemDataMap: Map[SierraItemNumber, SierraItemData])
    : List[Item[IdState.Identifiable]] = {
    val visibleItems =
      itemDataMap
        .filterNot {
          case (_, itemData) => itemData.deleted || itemData.suppressed
        }

    SierraPhysicalItemOrder(
      bibId,
      items = getPhysicalItems(bibId, visibleItems, bibData)
    )
  }

  private def getPhysicalItems(
    bibId: SierraBibNumber,
    sierraItemDataMap: Map[SierraItemNumber, SierraItemData],
    bibData: SierraBibData): List[Item[IdState.Identifiable]] = {

    // Some of the Sierra items have a location like "contained in above"
    // or "bound in above".
    //
    // This is only useful if you know the correct ordering of items on the bib,
    // which we don't.  This information isn't available in the REST API that
    // we use to get Sierra data.  See https://github.com/wellcomecollection/platform/issues/4993
    //
    // We assume that "in above" refers to another item on the same bib, so if the
    // non-above locations are unambiguous, we use them instead.
    val otherLocations =
      sierraItemDataMap
        .collect { case (id, itemData) => id -> itemData.location }
        .collect { case (id, Some(location)) => id -> location }
        .filterNot {
          case (_, loc) =>
            loc.name.toLowerCase.contains("above") || loc.name == "-" || loc.name == ""
        }
        .map {
          case (id, loc) =>
            SierraPhysicalLocationType.fromName(id, loc.name) match {
              case Some(LocationType.ClosedStores) =>
                (
                  Some(LocationType.ClosedStores),
                  LocationType.ClosedStores.label)
              case other => (other, loc.name)
            }
        }
        .toSeq
        .distinct

    val fallbackLocation = otherLocations match {
      case Seq((Some(locationType), label)) => Some((locationType, label))
      case _                                => None
    }

    sierraItemDataMap.values
      .foreach { itemData =>
        require(!itemData.deleted)
        require(!itemData.suppressed)
      }

    val items = sierraItemDataMap.map {
      case (itemId, itemData) =>
        transformItemData(
          bibId = bibId,
          itemId = itemId,
          itemData = itemData,
          bibData = bibData,
          fallbackLocation = fallbackLocation
        )
    }.toList

    tidyInferredTitles(items)
  }

  private def transformItemData(
    bibId: SierraBibNumber,
    itemId: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)]
  ): (Item[IdState.Identifiable], HasInferredTitle) = {
    debug(s"Attempting to transform $itemId")

    val location =
      getPhysicalLocation(bibId, itemId, itemData, bibData, fallbackLocation)

    val (title, hasInferredTitle) = getItemTitle(itemId, itemData)

    val item = Item(
      title = title,
      note = getItemNote(bibId, itemId, itemData, bibData, location),
      locations = List(location).flatten,
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Item",
          value = itemId.withCheckDigit
        ),
        otherIdentifiers = List(
          SourceIdentifier(
            identifierType = IdentifierType.SierraIdentifier,
            ontologyType = "Item",
            value = itemId.withoutCheckDigit
          )
        )
      )
    )

    (item, hasInferredTitle)
  }

  /** Create a title for the item.
    *
    * We use one of:
    *
    *   - field tag `v` for VOLUME
    *     https://documentation.iii.com/sierrahelp/Content/sril/sril_records_varfld_types_item.html
    *
    *   - The copyNo field from the Sierra API response, which we use to
    *     create a string like "Copy 2".
    *
    */
  private def getItemTitle(
    itemId: SierraItemNumber,
    data: SierraItemData): (Option[String], HasInferredTitle) = {
    val titleCandidates: List[String] =
      data.varFields
        .filter { _.fieldTag.contains("v") }
        .flatMap { getTitleFromVarfield }
        .map { _.trim }
        .filterNot { _ == "" }
        .distinct

    val copyNoTitle =
      data.copyNo.map { copyNo =>
        s"Copy $copyNo"
      }

    titleCandidates match {
      case Seq(title) => (Some(title), false)

      case Nil => (copyNoTitle, true)

      case multipleTitles =>
        warn(
          s"Multiple title candidates on item $itemId: ${titleCandidates.mkString("; ")}")
        (Some(multipleTitles.head), false)
    }
  }

  private def getTitleFromVarfield(vf: VarField): Option[String] =
    vf.content match {
      case Some(s) => Some(s)
      case None =>
        val title = vf.subfieldsWithTag("a").map { _.content }.mkString(" ")
        if (title.isEmpty) None else Some(title)
    }

  /** Tidy up the inferred titles (Copy 1, Copy 2, Copy 3, etc.)
    *
    * The purpose of a title is to help users distinguish between multiple items.
    * We remove the inferred title if they aren't doing that, in particular if:
    *
    *   1) There's only one item, and it has an inferred title
    *   2) Every item has the same inferred title
    *
    * Note: (1) is really a special case of (1) for the case when there's a single item.
    *
    * Note: we can't do this when we add the title to the individual items, because this rule
    * can only be applied when looking at the items together.
    *
    */
  private def tidyInferredTitles(
    items: List[(Item[IdState.Identifiable], HasInferredTitle)])
    : List[Item[IdState.Identifiable]] = {
    val inferredTitles =
      items.collect {
        case (Item(_, Some(title), _, _), inferredTitle) if inferredTitle =>
          title
      }

    val everyItemHasTheSameInferredTitle =
      inferredTitles.size == items.size && inferredTitles.toSet.size == 1

    if (everyItemHasTheSameInferredTitle) {
      items.map { case (it, _) => it.copy(title = None) }
    } else {
      items.collect { case (it, _) => it }
    }
  }

  /** Create a note for the item.
    *
    * Note that this isn't as simple as just using the contents of field tag "n" --
    * we may have copied the note to the access conditions instead, or decided to
    * discard it.
    */
  private def getItemNote(
    bibId: SierraBibNumber,
    itemId: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData,
    location: Option[PhysicalLocation]): Option[String] = {
    val (_, note) = SierraItemAccess(
      bibId,
      itemId,
      SierraAccessStatus.forBib(bibId, bibData),
      location.map(_.locationType),
      bibData,
      itemData
    )

    note
  }
}
