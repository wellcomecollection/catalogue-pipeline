package uk.ac.wellcome.models.work.internal

sealed trait Note {
  val content: String
}

case class GeneralNote(val content: String) extends Note
