package uk.ac.wellcome.calm_adapter

import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed trait CalmQuery {
  def keys: List[String]
  def value: String
  def logicalOperator = "OR"
  def relationalOperator = "="
  def queryExpression =
    keys
      .map(key => s"($key${relationalOperator}$value)")
      .mkString(logicalOperator)
}

object CalmQuery {

  case class ModifiedDate(date: LocalDate) extends CalmQuery {
    def keys = List("Modified")
    def value = formatDate(date)
  }

  case class CreatedOrModifiedDate(date: LocalDate) extends CalmQuery {
    def keys = List("Created", "Modified")
    def value = formatDate(date)
  }

  case object EmptyCreatedAndModifiedDate extends CalmQuery {
    def keys = List("Created", "Modified")
    override def logicalOperator = "AND"
    override def relationalOperator = "!="
    def value = "*"
  }

  case class RefNo(refNo: String) extends CalmQuery {
    def keys = List("RefNo")
    def value = refNo
  }

  def formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
