package weco.pipeline.transformer.sierra.data

import java.io.InputStream
import weco.storage.streaming.Codec._
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.work.Item
import weco.sierra.models.identifiers.{SierraBibNumber, SierraItemNumber}

/** The order of physical items in Sierra is significant, and can affect how
  * items should be requested by users.
  *
  * For example, you might have four items that are meant to go:
  *
  * Thing you can request Please request top item Another thing you can request
  * Please request top item
  *
  * This "Please request top item" occurs when two items have been bound
  * together (e.g. on the same frame) and they have to be requested together.
  *
  * This ordering is stored in a part of Sierra we can't access through the REST
  * API. If you sort only by item number (which we used to do), you might get
  * something like:
  *
  * Please request top item Please request top item Thing you can request
  * Another thing you can request
  *
  * which is actively unhelpful.
  *
  * Before we turned off wellcomelibrary.org, I scraped the "correct" ordering
  * by looking at the rendered HTML pages, and saved it in the JSON file that
  * supplies this class. If there was a non-standard order on wl.org, we'll use
  * the same order on wc.org.
  *
  * Motivation:
  *
  *   - This will make wc.org "no worse" than wl.org. Because the two sites will
  *     use the same ordering as each other (mostly), the new site won't be
  *     noticeably worse.
  *   - Using the fixed ordering is simpler than writing heuristics (e.g. based
  *     on item label), which could make the ordering better or worse.
  *   - Although this won't stay up-to-date with changes in Sierra, it gets us a
  *     reasonable improvement for not much effort.
  *
  * Long-term, we should find a better approach to this, but this alone
  * represents a non-trivial improvement in how we handle items.
  *
  * See https://github.com/wellcomecollection/platform/issues/4993
  */
object SierraPhysicalItemOrder {
  type OrderingMap = Map[SierraBibNumber, List[SierraItemNumber]]

  lazy val orderingMap: OrderingMap = {
    val stream: InputStream =
      getClass.getResourceAsStream("/item_ordering.json")

    typeCodec[OrderingMap].fromStream(stream).right.get
  }

  def apply(
    bibNumber: SierraBibNumber,
    items: List[Item[IdState.Identifiable]]
  ): List[Item[IdState.Identifiable]] = {

    val overrideOrder = orderingMap.getOrElse(bibNumber, default = List())

    val (inOrder, outOfOrder) = items.partition {
      it: Item[IdState.Identifiable] =>
        require(
          it.id.sourceIdentifier.identifierType == IdentifierType.SierraSystemNumber
        )
        val itemNumber = SierraItemNumber(it.id.sourceIdentifier.value)
        overrideOrder.contains(itemNumber)
    }

    val sortedInOrder = inOrder.sortBy {
      it =>
        val itemNumber = SierraItemNumber(it.id.sourceIdentifier.value)
        overrideOrder.indexOf(itemNumber)
    }

    val sortedOutOfOrder = outOfOrder.sortBy {
      it =>
        it.id.sourceIdentifier.value
    }

    sortedInOrder ++ sortedOutOfOrder
  }
}
