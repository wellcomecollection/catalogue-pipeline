package uk.ac.wellcome.models.work.internal

case class Genre[+State](
  label: String,
  concepts: List[AbstractConcept[State]] = Nil
)
