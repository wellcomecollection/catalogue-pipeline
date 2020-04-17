package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.transformable.sierra.{
  SierraBibNumber,
  SierraItemNumber
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraQueryOps
}

case class SierraItems(itemDataMap: Map[SierraItemNumber, SierraItemData])
    extends SierraTransformer
    with Logging
    with SierraLocation
    with SierraQueryOps {

  type Output = List[Item[Unminted]]

  /** We don't get the digital items from Sierra.
    *  The `dlnk` was previously used, but we now use the METS source
    */
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) = {
    getPhysicalItems(itemDataMap, bibData)
  }

  private def getPhysicalItems(
    sierraItemDataMap: Map[SierraItemNumber, SierraItemData],
    bibData: SierraBibData): List[Item[Unminted]] =
    sierraItemDataMap
      .filterNot {
        case (_: SierraItemNumber, itemData: SierraItemData) => itemData.deleted
      }
      .map {
        case (itemId: SierraItemNumber, itemData: SierraItemData) =>
          transformItemData(itemId, itemData, bibData)
      }
      .toList

  private def transformItemData(itemId: SierraItemNumber,
                                itemData: SierraItemData,
                                bibData: SierraBibData): Item[Unminted] = {
    debug(s"Attempting to transform $itemId")
    Item(
      title = getItemTitle(itemData),
      locations = getPhysicalLocation(itemData, bibData).toList,
      id = Identifiable(
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
