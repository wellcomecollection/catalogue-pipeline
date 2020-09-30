package uk.ac.wellcome.models.work.internal

case class Subject[+State](
  id: State,
  label: String,
  concepts: List[AbstractRootConcept[State]] = Nil
) extends HasId[State]

object Subject {

  def apply[State >: IdState.Unidentifiable.type](
    label: String,
    concepts: List[AbstractRootConcept[State]]): Subject[State] =
    Subject(
      id = IdState.Unidentifiable,
      label = label,
      concepts = concepts
    )
}
