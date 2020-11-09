package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraQueryOps
}
import uk.ac.wellcome.sierra_adapter.model.SierraItemNumber

case class SierraItems(itemDataMap: Map[SierraItemNumber, SierraItemData])
    extends SierraDataTransformer
    with Logging
    with SierraLocation
    with SierraQueryOps {

  type Output = List[Item[IdState.Unminted]]

  /** We don't get the digital items from Sierra.
    * The `dlnk` was previously used, but we now use the METS source.
    *
    * So the output is deterministic here we sort all items by the
    * sierra-identifier
    */
  def apply(bibData: SierraBibData) =
    getPhysicalItems(itemDataMap, bibData)
      .sortBy { item =>
        item.id match {
          case IdState.Unidentifiable          => None
          case IdState.Identifiable(_, ids, _) => ids.headOption.map(_.value)
        }
      }

  private def getPhysicalItems(
    sierraItemDataMap: Map[SierraItemNumber, SierraItemData],
    bibData: SierraBibData): List[Item[IdState.Unminted]] =
    sierraItemDataMap
      .filterNot {
        case (_: SierraItemNumber, itemData: SierraItemData) => itemData.deleted
      }
      .map {
        case (itemId: SierraItemNumber, itemData: SierraItemData) =>
          transformItemData(itemId, itemData, bibData)
      }
      .toList

  private def transformItemData(
    itemId: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData): Item[IdState.Unminted] = {
    debug(s"Attempting to transform $itemId")
    Item(
      title = getItemTitle(itemData),
      locations = getPhysicalLocation(itemData, bibData).toList,
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

  private def getItemTitle(data: SierraItemData) =
    data.varFields.withFieldTag("v").contents.headOption
}
