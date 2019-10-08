package uk.ac.wellcome.models.work.internal

case class Genre[+T <: IdentityState[AbstractConcept]](
  label: String,
  concepts: List[T] = List(),
  ontologyType: String = "Genre"
)
