package uk.ac.wellcome.models.work.internal

case class Subject[+DataId](
  id: DataId,
  label: String,
  concepts: List[AbstractRootConcept[DataId]] = Nil,
  ontologyType: String = "Subject"
) extends HasId[DataId]

object Subject {

  def apply[DataId >: Id.Unidentifiable.type](
    label: String,
    concepts: List[AbstractRootConcept[DataId]]): Subject[DataId] =
    Subject(
      id = Id.Unidentifiable,
      label = label,
      concepts = concepts
    )
}
