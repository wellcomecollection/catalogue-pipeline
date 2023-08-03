package weco.pipeline.calm_api_client

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * CalmQuery is used to construct the "Expr" parameter of
 * Calm search queries. These look something like:
 * (key=value)OR(key!=value)
 *
 * Some documentation of this parameter is available in the
 * "Search" section here: https://wt-calm.wellcome.ac.uk/CalmAPI/help/
 *
 * Where this fits into a SOAP query is documented here:
 * https://wt-calm.wellcome.ac.uk/CalmAPI/help/example_search.htm
 */
sealed trait CalmQuery {
  def queryExpression: String
}

// These general classes need to be sealed so we can derive codecs for
// the specific cases in the companion object

// Query leaves become expressions like `(key=value)` in full queries
sealed class QueryLeaf(
  val key: String,
  val value: String,
  val relationalOperator: String = "="
) extends CalmQuery {
  def queryExpression: String = s"($key$relationalOperator$value)"
}
// Query nodes join query leaves together with booleans like `(a=b)OR(c=d)`
sealed class QueryNode(
  val left: CalmQuery,
  val right: CalmQuery,
  val logicalOperator: String = "OR"
) extends CalmQuery {
  def queryExpression: String =
    left.queryExpression + logicalOperator + right.queryExpression
}

object QueryLeaf {
  def unapply(q: QueryLeaf): Option[(String, String, String)] =
    Some((q.key, q.value, q.relationalOperator))
}

object QueryNode {
  def unapply(q: QueryNode): Option[(CalmQuery, CalmQuery, String)] =
    Some((q.left, q.right, q.logicalOperator))
}

object CalmQuery {
  // Keep these as case classes rather than `def`s so they're easy to (de)serialize

  // (Modified=<date>)
  case class ModifiedDate(date: LocalDate)
      extends QueryLeaf(key = "Modified", value = formatDate(date))

  // (Created=<date>)
  case class CreatedDate(date: LocalDate)
      extends QueryLeaf(key = "Created", value = formatDate(date))

  // (Modified=<date>)OR(Created=<date>)
  case class CreatedOrModifiedDate(date: LocalDate)
      extends QueryNode(
        left = CreatedDate(date),
        right = ModifiedDate(date),
        logicalOperator = "OR"
      )

  // (Created!=*)AND(Modified!=*)
  case object EmptyCreatedAndModifiedDate
      extends QueryNode(
        left = emptyKey("Created"),
        right = emptyKey("Modified"),
        logicalOperator = "AND"
      )

  // (RefNo=refNo)
  case class RefNo(refNo: String)
      extends QueryLeaf(key = "RefNo", value = refNo)

  // RecordId queries need to have double quotes for some reason
  // (RecordId="<id>")
  case class RecordId(id: String)
      extends QueryLeaf(key = "RecordId", value = s""""$id"""")

  // (key!=*)
  def emptyKey(key: String) =
    new QueryLeaf(key = key, value = "*", relationalOperator = "!=")

  def formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  implicit class CalmQueryOps(queryA: CalmQuery) {
    def or(queryB: CalmQuery): CalmQuery = new QueryNode(
      left = queryA,
      right = queryB,
      logicalOperator = "OR"
    )

    def and(queryB: CalmQuery): CalmQuery = new QueryNode(
      left = queryA,
      right = queryB,
      logicalOperator = "AND"
    )
  }

  // For some reason Circe no longer tolerates auto-deriving this decoder
  implicit val decoder: Decoder[CalmQuery] = deriveDecoder[CalmQuery]
}
