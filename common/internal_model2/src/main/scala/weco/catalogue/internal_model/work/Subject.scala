package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.{HasId, IdState}

case class Subject[+State](
  id: State,
  label: String,
  concepts: List[AbstractRootConcept[State]] = Nil
) extends HasId[State]

object Subject {

  def apply[State >: IdState.Unidentifiable.type](
    label: String,
    concepts: List[AbstractRootConcept[State]]
  ): Subject[State] =
    Subject(
      id = IdState.Unidentifiable,
      label = label,
      concepts = concepts
    )
}
