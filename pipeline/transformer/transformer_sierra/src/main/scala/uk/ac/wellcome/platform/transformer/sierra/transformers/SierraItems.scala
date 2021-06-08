package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.transformer.sierra.data.SierraPhysicalItemOrder
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.rules.SierraPhysicalLocationType
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{
  SierraBibData,
  SierraBibNumber,
  SierraItemData,
  SierraItemNumber
}

object SierraItems extends Logging with SierraLocation with SierraQueryOps {

  type Output = List[Item[IdState.Unminted]]

  /** We don't get the digital items from Sierra.
    * The `dlnk` was previously used, but we now use the METS source.
    *
    * So the output is deterministic here we sort all items by the
    * sierra-identifier.  We want to revisit this at some point.
    * See https://github.com/wellcomecollection/platform/issues/4993
    */
  def apply(bibId: SierraBibNumber,
            bibData: SierraBibData,
            itemDataMap: Map[SierraItemNumber, SierraItemData]): List[Item[IdState.Identifiable]] = {
    val visibleItems =
      itemDataMap
        .filterNot {
          case (_, itemData) => itemData.deleted || itemData.suppressed
        }

    SierraPhysicalItemOrder(
      bibId, items = getPhysicalItems(bibId, visibleItems, bibData)
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
        .collect {
          case (id, SierraItemData(_, _, _, Some(location), _, _)) =>
            id -> location
        }
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

    sierraItemDataMap
      .filterNot {
        case (_: SierraItemNumber, itemData: SierraItemData) => itemData.deleted
      }
      .map {
        case (itemId: SierraItemNumber, itemData: SierraItemData) =>
          transformItemData(
            bibId = bibId,
            itemId = itemId,
            itemData = itemData,
            bibData = bibData,
            fallbackLocation = fallbackLocation
          )
      }
      .toList
  }

  private def transformItemData(
    bibId: SierraBibNumber,
    itemId: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)])
    : Item[IdState.Identifiable] = {
    debug(s"Attempting to transform $itemId")
    Item(
      title = getItemTitle(itemId, itemData),
      locations = getPhysicalLocation(
        bibId,
        itemId,
        itemData,
        bibData,
        fallbackLocation).toList,
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
  }

  private def getItemTitle(itemId: SierraItemNumber,
                           data: SierraItemData): Option[String] = {
    // For the title of the item, we look at the varfields with
    // field tag ǂv, and use either their "contents" or the content of
    // the subfield with tag ǂa.
    val titleCandidates: List[String] =
      data.varFields
        .filter { _.fieldTag.contains("v") }
        .flatMap { vf =>
          List(vf.content) ++ vf.subfieldsWithTag("a").map { sf =>
            Some(sf.content)
          }
        }
        .flatten
        .map { _.trim }
        .filterNot { _ == "" }
        .distinct

    titleCandidates match {
      case Seq(title) => Some(title)
      case Nil        => None
      case multipleTitles =>
        warn(
          s"Multiple title candidates on item $itemId: ${titleCandidates.mkString("; ")}")
        Some(multipleTitles.head)
    }
  }
}
