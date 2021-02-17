package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraQueryOps
}
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraItemNumber}

case class SierraItems(itemDataMap: Map[SierraItemNumber, SierraItemData])
    extends SierraIdentifiedDataTransformer
    with Logging
    with SierraLocation
    with SierraQueryOps {

  type Output = List[Item[IdState.Unminted]]

  /** We don't get the digital items from Sierra.
    * The `dlnk` was previously used, but we now use the METS source.
    *
    * So the output is deterministic here we sort all items by the
    * sierra-identifier.  We want to revisit this at some point.
    * See https://github.com/wellcomecollection/platform/issues/4993
    */
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getPhysicalItems(bibId, itemDataMap, bibData)
      .sortBy { item =>
        item.id match {
          case IdState.Unidentifiable          => None
          case IdState.Identifiable(_, ids, _) => ids.headOption.map(_.value)
        }
      }

  private def getPhysicalItems(
    bibId: SierraBibNumber,
    sierraItemDataMap: Map[SierraItemNumber, SierraItemData],
    bibData: SierraBibData): List[Item[IdState.Unminted]] = {

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
      sierraItemDataMap.values
        .filterNot { _.deleted }
        .collect { case SierraItemData(_, Some(location), _, _, _) => location }
        .filterNot { loc =>
          loc.name.toLowerCase.contains("above") || loc.name == "-" || loc.name == ""
        }
        .map { loc =>
          SierraPhysicalLocationType.fromName(loc.name) match {
            case Some(LocationType.ClosedStores) =>
              (Some(LocationType.ClosedStores), LocationType.ClosedStores.label)
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
    : Item[IdState.Unminted] = {
    debug(s"Attempting to transform $itemId")
    Item(
      title = getItemTitle(itemId, itemData),
      locations =
        getPhysicalLocation(bibId, itemData, bibData, fallbackLocation).toList,
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType("sierra-system-number"),
          ontologyType = "Item",
          value = itemId.withCheckDigit
        ),
        otherIdentifiers = List(
          SourceIdentifier(
            identifierType = IdentifierType("sierra-identifier"),
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
