package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[Id <: WithSourceIdentifier] {
  val id: Id
  val ontologyType: String
}

case class SourceWork[Id <: WithSourceIdentifier](
  id: Id,
  ontologyType: String = "Work"
) extends ImageSource[Id]
