package weco.pipeline.calm_api_client


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
sealed trait CalmQueryBase {
  def queryExpression: String
}

// Query leaves become expressions like `(key=value)` in full queries
class QueryLeaf(
  val key: String,
  val value: String,
  val relationalOperator: String = "="
) extends CalmQueryBase {
  def queryExpression: String = s"($key$relationalOperator$value)"
}
// Query nodes join query leaves together with booleans like `(a=b)OR(c=d)`
class QueryNode(
  val left: CalmQueryBase,
  val right: CalmQueryBase,
  val logicalOperator: String = "OR"
) extends CalmQueryBase {
  def queryExpression: String =
    left.queryExpression + logicalOperator + right.queryExpression
}

object QueryLeaf {
  def unapply(q: QueryLeaf): Option[(String, String, String)] =
    Some((q.key, q.value, q.relationalOperator))
}

object QueryNode {
  def unapply(q: QueryNode): Option[(CalmQueryBase, CalmQueryBase, String)] =
    Some((q.left, q.right, q.logicalOperator))
}

object CalmQueryBase {
  implicit class CalmQueryOps(queryA: CalmQueryBase) {
    def or(queryB: CalmQueryBase): CalmQueryBase = new QueryNode(
      left = queryA,
      right = queryB,
      logicalOperator = "OR"
    )

    def and(queryB: CalmQueryBase): CalmQueryBase = new QueryNode(
      left = queryA,
      right = queryB,
      logicalOperator = "AND"
    )
  }
}

sealed trait CalmQuery extends CalmQueryBase
object CalmQuery {
  // (Modified=<date>)
  case class ModifiedDate(date: LocalDate)
      extends QueryLeaf(key = "Modified", value = formatDate(date))
      with CalmQuery

  // (Created=<date>)
  case class CreatedDate(date: LocalDate)
      extends QueryLeaf(key = "Created", value = formatDate(date))
      with CalmQuery

  // (Modified=<date>)OR(Created=<date>)
  case class CreatedOrModifiedDate(date: LocalDate)
      extends QueryNode(
        left = CreatedDate(date),
        right = ModifiedDate(date),
        logicalOperator = "OR"
      )
      with CalmQuery

  // (Created!=*)AND(Modified!=*)
  case object EmptyCreatedAndModifiedDate
      extends QueryNode(
        left = emptyKey("Created"),
        right = emptyKey("Modified"),
        logicalOperator = "AND"
      )
      with CalmQuery

  // (RefNo=refNo)
  case class RefNo(refNo: String)
      extends QueryLeaf(key = "RefNo", value = refNo)
      with CalmQuery

  // RecordId queries need to have double quotes for some reason
  // (RecordId="<id>")
  case class RecordId(id: String)
      extends QueryLeaf(key = "RecordId", value = s""""$id"""")
      with CalmQuery

  // (key!=*)
  def emptyKey(key: String) =
    new QueryLeaf(key = key, value = "*", relationalOperator = "!=")

  def formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

}
