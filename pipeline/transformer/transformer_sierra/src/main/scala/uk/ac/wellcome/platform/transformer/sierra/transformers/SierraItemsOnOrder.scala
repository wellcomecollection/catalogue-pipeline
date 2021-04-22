package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraItemData,
  SierraOrderData
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.{
  SierraItemNumber,
  SierraOrderNumber,
  TypedSierraRecordNumber
}

import java.text.SimpleDateFormat
import java.util.Date
import scala.util.Try

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
  * The Sierra documentation for fixed fields on order records is useful reading:
  * https://documentation.iii.com/sierrahelp/Default.htm#sril/sril_records_fixed_field_types_order.html%3FTocPath%3DSierra%2520Reference%7CHow%2520Innovative%2520Systems%2520Store%2520Information%7CFixed-length%2520Fields%7C_____11
  *
  */
object SierraItemsOnOrder {
  def apply(
    id: TypedSierraRecordNumber,
    itemDataMap: Map[SierraItemNumber, SierraItemData],
    orderDataMap: Map[SierraOrderNumber, SierraOrderData]
  ): List[Item[IdState.Unidentifiable.type]] =
    if (itemDataMap.isEmpty) {
      orderDataMap
        .toList
        .filterNot { case (_, orderData) => orderData.suppressed || orderData.deleted }
        .sortBy { case (id, _) => id.withoutCheckDigit }
        .flatMap { case (_, orderData) => createItem(id, orderData) }
    } else {
      List()
    }

  private def createItem(id: TypedSierraRecordNumber, order: SierraOrderData): Option[Item[IdState.Unidentifiable.type]] =
    (getStatus(order), getOrderDate(order), getCopies(order)) match {
      // status 'o' = "On order"
      case (status, orderedDate, copies) if status.contains("o") =>
        Some(
          Item(
            title = None,
            locations = List(
              PhysicalLocation(
                locationType = LocationType.OnOrder,
                label = createOnOrderMessage(orderedDate, copies)
              )
            )
          )
        )
    }

  // Fixed field 20 = STATUS
  private def getStatus(order: SierraOrderData): Option[String] =
    order.fixedFields.get("20").map { _.value }

  private val rdateFormat = new SimpleDateFormat("yyyy-MM-dd")

  // Fixed field 13 = ODATE.  This is usually a date in the form YYYY-MM-DD.
  private def getOrderDate(order: SierraOrderData): Option[Date] =
    order.fixedFields.get("13").map { _.value }
      .flatMap { d => Try(rdateFormat.parse(d)).toOption }

  // Fixed field 5 = COPIES
  private def getCopies(order: SierraOrderData): Option[Int] =
    order.fixedFields.get("5").map { _.value }
      .flatMap { c => Try(c.toInt).toOption }

  private val displayFormat = new SimpleDateFormat("d MMMM yyyy")

  private def createOnOrderMessage(maybeOrderedDate: Option[Date], maybeCopies: Option[Int]): String =
    (maybeOrderedDate, maybeCopies) match {
      case (Some(orderedDate), Some(copies)) =>
        s"$copies ${if (copies == 1) "copy" else "copies"} ordered for Wellcome Collection on ${displayFormat.format(orderedDate)}"

      case (None, Some(copies)) =>
        s"$copies ${if (copies == 1) "copy" else "copies"} ordered for Wellcome Collection"

      case (Some(orderedDate), None) =>
        s"Ordered for Wellcome Collection on ${displayFormat.format(orderedDate)}"

      case (None, None) =>
        "Ordered for Wellcome Collection"
    }
}
