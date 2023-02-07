package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
import weco.catalogue.internal_model.work.Item
import weco.sierra.models.data.{SierraBibData, SierraOrderData}
import weco.sierra.models.identifiers.{
  SierraOrderNumber,
  TypedSierraRecordNumber
}

import java.text.SimpleDateFormat
import java.util.Date
import scala.util.Try

/** This transformer creates catalogue items that correspond to items that are
  * "on order" or "awaiting cataloguing" -- which don't have their own item
  * record yet.
  *
  * To understand how this works, it's useful to understand the ordering
  * process:
  *
  * 1) A staff member orders a book. They create a skeleton bib record in
  * Sierra, which is linked to an order record with status 'o' ("on order"). At
  * this point, there are no item records.
  *
  * 2) When the book arrives, it's "received". The RDATE on the order record
  * gets populated, the status is updated to 'a' ("fully paid"), and invoice
  * information gets attached to the order record.
  *
  * 3) At some point after that, an item record is created. This supercedes the
  * order record.
  *
  * Note that born-digital objects do not necessarily get item records: that can
  * be supplied separately, e.g. from the METS. In this case, we should look at
  * the CAT DATE (cataloguing date) fixed field to see we shouldn't add any
  * order items.
  *
  * The Sierra documentation for fixed fields on order records is useful
  * reading:
  * https://documentation.iii.com/sierrahelp/Default.htm#sril/sril_records_fixed_field_types_order.html%3FTocPath%3DSierra%2520Reference%7CHow%2520Innovative%2520Systems%2520Store%2520Information%7CFixed-length%2520Fields%7C_____11
  */
object SierraItemsOnOrder extends Logging {
  def apply(
    id: TypedSierraRecordNumber,
    bibData: SierraBibData,
    hasItems: Boolean,
    orderDataMap: Map[SierraOrderNumber, SierraOrderData]
  ): List[Item[IdState.Unidentifiable.type]] =
    if (!hasItems && !bibData.hasCatDate) {
      orderDataMap.toList
        .filterNot {
          case (_, orderData) =>
            orderData.suppressed || orderData.deleted
        }
        .sortBy { case (id, _) => id.withoutCheckDigit }
        .flatMap { case (_, orderData) => createItem(id, orderData) }
        .distinct
    } else {
      List()
    }

  private def createItem(
    id: TypedSierraRecordNumber,
    order: SierraOrderData
  ): Option[Item[IdState.Unidentifiable.type]] =
    (getStatus(order), getOrderDate(order), getReceivedDate(order)) match {

      // status 'o' = "On order"
      // status 'c' = "Serial on order"
      // status 'a' = "fully paid"
      //
      // We create an item with a message something like "Ordered for Wellcome Collection on 1 Jan 2001"
      case (Some(status), orderedDate, receivedDate)
          if (status == "o" || status == "c" || status == "a") &&
            receivedDate.isEmpty =>
        Some(
          Item(
            title = None,
            locations = List(
              PhysicalLocation(
                locationType = LocationType.OnOrder,
                label = createOnOrderMessage(orderedDate)
              )
            )
          )
        )

      // status 'a' = "Fully paid", RDATE = "when we actually received the thing"
      //
      // We create an item with a message like "Awaiting cataloguing for Wellcome Collection"
      // We don't expose the received date publicly (in case an item has been in the queue
      // for a long time) -- but we do expect it to be there for these records.
      case (Some(status), _, receivedDate)
          if status == "a" && receivedDate.isDefined =>
        Some(
          Item(
            title = None,
            locations = List(
              PhysicalLocation(
                locationType = LocationType.OnOrder,
                label = "Awaiting cataloguing for Wellcome Collection"
              )
            )
          )
        )

      // We're deliberately quite conservative here -- if we're not sure what an order
      // means, we ignore it.  I don't know how many orders this will affect, and how many
      // will be ignored because they're suppressed/there are other items.
      case (Some(status), _, receivedDate)
          if status == "a" && receivedDate.isEmpty =>
        warn(
          s"${id.withCheckDigit}: order has STATUS 'a' (fully paid) but no RDATE.  Where is this item?"
        )
        None

      case (status, _, _) =>
        warn(
          s"${id.withCheckDigit}: order has unrecognised STATUS $status.  How do we handle it?"
        )
        None
    }

  // Fixed field 20 = STATUS
  private def getStatus(order: SierraOrderData): Option[String] =
    order.fixedFields.get("20").map { _.value }

  private val marcDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  // Fixed field 13 = ODATE.  This is usually a date in the form YYYY-MM-DD.
  private def getOrderDate(order: SierraOrderData): Option[Date] =
    order.fixedFields
      .get("13")
      .map { _.value }
      .flatMap {
        d =>
          Try(marcDateFormat.parse(d)).toOption
      }

  // Fixed field 17 = RDATE.  This is usually a date in the form YYYY-MM-DD.
  private def getReceivedDate(order: SierraOrderData): Option[Date] =
    order.fixedFields
      .get("17")
      .map { _.value }
      .flatMap {
        d =>
          Try(marcDateFormat.parse(d)).toOption
      }

  private val displayFormat = new SimpleDateFormat("d MMMM yyyy")

  private def createOnOrderMessage(maybeOrderedDate: Option[Date]): String =
    maybeOrderedDate match {
      case Some(orderedDate) =>
        s"Ordered for Wellcome Collection on ${displayFormat.format(orderedDate)}"

      case None =>
        "Ordered for Wellcome Collection"
    }

  implicit class BibDataCatDateOps(bibData: SierraBibData) {
    // See https://documentation.iii.com/sierrahelp/Default.htm#sril/sril_records_fixed_field_types_biblio.html%3FTocPath%3DSierra%2520Reference%7CHow%2520Innovative%2520Systems%2520Store%2520Information%7CFixed-length%2520Fields%7C_____3
    def hasCatDate: Boolean =
      bibData.fixedFields.contains("28")
  }
}
