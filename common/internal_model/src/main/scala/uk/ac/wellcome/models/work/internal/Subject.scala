package uk.ac.wellcome.models.work.internal

case class Subject[+Id](
  id: Id,
  label: String,
  concepts: List[AbstractRootConcept[Id]] = Nil,
  ontologyType: String = "Subject"
) extends HasIdState[Id]

object Subject {

  def apply[Id >: Unidentifiable.type](
    label: String,
    concepts: List[AbstractRootConcept[Id]]): Subject[Id] =
    Subject(
      id = Unidentifiable,
      label = label,
      concepts = concepts
    )
}
