package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.locations.{
  LocationType,
  PhysicalLocation,
  PhysicalLocationType
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.rules.{SierraItemAccess, SierraPhysicalLocationType}
import weco.pipeline.transformer.sierra.data.SierraPhysicalItemOrder
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.{SierraBibData, SierraItemData}
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

object SierraItems extends Logging with SierraPhysicalLocation with SierraQueryOps {

  type Output = List[Item[IdState.Unminted]]

  /** We don't get the digital items from Sierra. The `dlnk` was previously used, but we now use the
    * METS source.
    *
    * So the output is deterministic here we sort all items by the sierra-identifier. We want to
    * revisit this at some point. See https://github.com/wellcomecollection/platform/issues/4993
    */
  def apply(
    bibId: SierraBibNumber,
    bibData: SierraBibData,
    itemDataEntries: Seq[SierraItemData]
  ): List[Item[IdState.Identifiable]] = {
    val visibleItems =
      itemDataEntries
        .filterNot {
          itemData =>
            itemData.deleted || itemData.suppressed
        }

    SierraPhysicalItemOrder(
      bibId,
      items = getPhysicalItems(visibleItems, bibData)
    )
  }

  private def getPhysicalItems(
    itemDataEntries: Seq[SierraItemData],
    bibData: SierraBibData
  ): List[Item[IdState.Identifiable]] = {

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
      itemDataEntries
        .map {
          itemData =>
            itemData.id -> itemData.location
        }
        .collect { case (id, Some(location)) => id -> location }
        .filterNot {
          case (_, loc) =>
            loc.name.toLowerCase.contains(
              "above"
            ) || loc.name == "-" || loc.name == ""
        }
        .map {
          case (id, loc) =>
            SierraPhysicalLocationType.fromName(id, loc.name) match {
              case Some(LocationType.ClosedStores) =>
                (
                  Some(LocationType.ClosedStores),
                  LocationType.ClosedStores.label
                )
              case other => (other, loc.name)
            }
        }
        .distinct

    val fallbackLocation = otherLocations match {
      case Seq((Some(locationType), label)) => Some((locationType, label))
      case _                                => None
    }

    itemDataEntries
      .foreach {
        itemData =>
          require(!itemData.deleted)
          require(!itemData.suppressed)
      }

    val items = itemDataEntries.map {
      itemData =>
        transformItemData(
          itemData = itemData,
          bibData = bibData,
          fallbackLocation = fallbackLocation
        )
    }.toList

    tidyTitles(items)
  }

  // A "automated" title is a title that we created automatically, as opposed
  // to one that was written by a human cataloguer.
  private type HasAutomatedTitle = Boolean

  private def transformItemData(
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)]
  ): (Item[IdState.Identifiable], HasAutomatedTitle) = {
    debug(s"Attempting to transform ${itemData.id.withCheckDigit}")

    val location =
      getPhysicalLocation(
        itemData = itemData,
        bibData = bibData,
        fallbackLocation = fallbackLocation
      )

    val (title, hasInferredTitle) = getItemTitle(itemData)

    val item = Item(
      title = title,
      note = getItemNote(
        itemData = itemData,
        location = location
      ),
      locations = List(location).flatten,
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Item",
          value = itemData.id.withCheckDigit
        ),
        otherIdentifiers = List(
          SourceIdentifier(
            identifierType = IdentifierType.SierraIdentifier,
            ontologyType = "Item",
            value = itemData.id.withoutCheckDigit
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
    *   - field tag `v` for VOLUME. This is written by a cataloguer.
    *     https://documentation.iii.com/sierrahelp/Content/sril/sril_records_varfld_types_item.html
    *
    *   - The copyNo field from the Sierra API response, which we use to create a string like "Copy
    *     2".
    *
    * Elsewhere in this class, this is called an "automated title", because we're creating the title
    * automatically rather than using text written by a cataloguer. In general, we prefer the
    * human-written title where possible.
    */
  private def getItemTitle(
    data: SierraItemData
  ): (Option[String], HasAutomatedTitle) = {
    val titleCandidates: List[String] =
      data.varFields
        .filter { _.fieldTag.contains("v") }
        .flatMap { getTitleFromVarfield }
        .map { _.trim }
        .filterNot { _ == "" }
        .distinct

    val copyNoTitle =
      data.copyNo.map {
        copyNo =>
          s"Copy $copyNo"
      }

    titleCandidates match {
      case Seq(title) => (Some(title), false)

      case Nil => (copyNoTitle, true)

      case multipleTitles =>
        warn(
          s"Multiple title candidates on item ${data.id}: ${titleCandidates.mkString("; ")}"
        )
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

  /** Tidy up the automated titles (Copy 1, Copy 2, Copy 3, etc.)
    *
    * The purpose of a title is to help users distinguish between multiple items. We remove the
    * automated title if they aren't doing that, in particular if:
    *
    * 1) There's only one item, and it has an inferred title 2) Every item has the same inferred
    * title
    *
    * Note: (1) is really a special case of (2) for the case when there's a single item.
    *
    * Note: we can't do this when we add the title to the individual items, because this rule can
    * only be applied when looking at the items together.
    */
  private def tidyTitles(
    items: List[(Item[IdState.Identifiable], HasAutomatedTitle)]
  ): List[Item[IdState.Identifiable]] = {
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
    * Note that this isn't as simple as just using the contents of field tag "n"
    * -- we may have copied the note to the access conditions instead, or decided to discard it.
    */
  private def getItemNote(
    itemData: SierraItemData,
    location: Option[PhysicalLocation]
  ): Option[String] = {
    val (_, note) = SierraItemAccess(
      location = location.map(_.locationType),
      itemData = itemData
    )

    note
  }
}
