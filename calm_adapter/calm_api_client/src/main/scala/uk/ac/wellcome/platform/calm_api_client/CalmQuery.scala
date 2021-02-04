package uk.ac.wellcome.platform.calm_api_client

import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed trait CalmQuery {
  def queryExpression: String
}

// These general classes need to be sealed so we can derive codecs for
// the specific cases in the companion object
sealed class QueryLeaf(
  key: String,
  value: String,
  relationalOperator: String = "="
) extends CalmQuery {
  def queryExpression: String = s"($key$relationalOperator$value)"
}
sealed class QueryNode(
  left: CalmQuery,
  right: CalmQuery,
  logicalOperator: String = "OR"
) extends CalmQuery {
  def queryExpression: String =
    left.queryExpression + logicalOperator + right.queryExpression
}

object CalmQuery {
  // Keep these as case classes rather than `def`s so they're easy to (de)serialize

  case class ModifiedDate(date: LocalDate)
      extends QueryLeaf(key = "Modified", value = formatDate(date))

  case class CreatedDate(date: LocalDate)
      extends QueryLeaf(key = "Created", value = formatDate(date))

  case class CreatedOrModifiedDate(date: LocalDate)
      extends QueryNode(
        left = CreatedDate(date),
        right = ModifiedDate(date),
        logicalOperator = "OR"
      )

  case object EmptyCreatedAndModifiedDate
      extends QueryNode(
        left = emptyKey("Created"),
        right = emptyKey("Modified"),
        logicalOperator = "AND"
      )

  case class RefNo(refNo: String)
      extends QueryLeaf(key = "RefNo", value = refNo)

  // RecordId queries need to have double quotes for some reason
  case class RecordId(id: String)
      extends QueryLeaf(key = "RecordId", value = s""""$id"""")

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
}
