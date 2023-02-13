package weco.catalogue.internal_model.work

case class Genre[+State](
  label: String,
  concepts: List[AbstractConcept[State]] = Nil
)
