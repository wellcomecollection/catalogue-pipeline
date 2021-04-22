package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraItemData,
  SierraOrderData
}
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.{
  SierraItemNumber,
  SierraOrderNumber,
  TypedSierraRecordNumber
}

/** This transformer creates catalogue items that correspond to items that
  * are "on order" or "awaiting cataloguing" -- which don't have their own
  * item record yet.
  *
  *
  * To understand how this works, it's useful to understand the ordering
  * process:
  *
  *   1)  A staff member orders a book.  They create a skeleton bib record
  *       in Sierra, which is linked to an order record with status 'o' ("on order").
  *       At this point, there are no item records.
  *
  *   2)  When the book arrives, it's "received".  The RDATE on the order
  *       record gets populated, the status is updated to 'a' ("fully paid"),
  *       and invoice information gets attached to the order record.
  *
  *   3)  At some point after that, an item record is created.  This supercedes
  *       the order record.
  *
  */
object SierraItemsOnOrder {
  def apply(
    id: TypedSierraRecordNumber,
    itemDataMap: Map[SierraItemNumber, SierraItemData],
    orderDataMap: Map[SierraOrderNumber, SierraOrderData]
  ): List[Item[Unidentified]] = {
    List()
  }
}
