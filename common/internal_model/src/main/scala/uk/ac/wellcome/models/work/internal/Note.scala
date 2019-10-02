package uk.ac.wellcome.models.work.internal

sealed trait Note {
  val content: String
}

case class GeneralNote(val content: String) extends Note

case class BibliographicalInformation(val content: String) extends Note

case class FundingInformation(val content: String) extends Note

case class TimeAndPlaceNote(val content: String) extends Note
