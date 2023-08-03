package weco.pipeline.calm_api_client

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

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
sealed case class QueryLeaf(
  key: String,
  value: String,
  relationalOperator: String = "="
) extends CalmQuery {
  def queryExpression: String = s"($key$relationalOperator$value)"
}
// Query nodes join query leaves together with booleans like `(a=b)OR(c=d)`
sealed case class QueryNode(
  left: CalmQuery,
  right: CalmQuery,
  logicalOperator: String = "OR"
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
  // (Modified=<date>)
  def ModifiedDate(date: LocalDate) =
    new QueryLeaf(key = "Modified", value = formatDate(date))

  // (Created=<date>)
  def CreatedDate(date: LocalDate) =
    new QueryLeaf(key = "Created", value = formatDate(date))

  // (Modified=<date>)OR(Created=<date>)
  def CreatedOrModifiedDate(date: LocalDate) = new QueryNode(
    left = CreatedDate(date),
    right = ModifiedDate(date),
    logicalOperator = "OR"
  )

  // (Created!=*)AND(Modified!=*)
  def EmptyCreatedAndModifiedDate = new QueryNode(
    left = emptyKey("Created"),
    right = emptyKey("Modified"),
    logicalOperator = "AND"
  )

  // (RefNo=refNo)
  def RefNo(refNo: String) = new QueryLeaf(key = "RefNo", value = refNo)

  // RecordId queries need to have double quotes for some reason
  // (RecordId="<id>")
  def RecordId(id: String) =
    new QueryLeaf(key = "RecordId", value = s""""$id"""")

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

  // For some reason Circe no longer tolerates auto-deriving these
  implicit val calmQueryDecoder: Decoder[CalmQuery] = deriveDecoder[CalmQuery]
  implicit val calmQueryEncoder: Encoder[CalmQuery] = deriveEncoder[CalmQuery]
}
