package uk.ac.wellcome.models.work.internal

case class Genre[+Id](
  label: String,
  concepts: List[AbstractConcept[Id]] = Nil,
  ontologyType: String = "Genre"
)
