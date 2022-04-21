package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.Relation

case class DisplayRelation(
  id: Option[String],
  title: Option[String],
  referenceNumber: Option[String] = None,
  partOf: Option[List[DisplayRelation]] = None,
  totalParts: Int,
  totalDescendentParts: Int,
  @JsonKey("type") ontologyType: String = "Work"
)

object DisplayRelation {

  def apply(relation: Relation): DisplayRelation =
    DisplayRelation(
      id = relation.id.map { _.underlying },
      title = relation.title,
      referenceNumber = relation.collectionPath.flatMap(_.label),
      ontologyType = DisplayWork.displayWorkType(relation.workType),
      totalParts = relation.numChildren,
      totalDescendentParts = relation.numDescendents
    )
}
