package uk.ac.wellcome.calm_adapter

import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed trait CalmQuery {
  def key: String
  def value: String
  def queryExpression = s"$key=$value"
}

object CalmQuery {

  case class ModifiedDate(date: LocalDate) extends CalmQuery {
    def key = "Modified"
    def value = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
  }
}
