package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.work.Relation

@Schema(name = "Work")
case class DisplayRelation(
  @Schema(
    accessMode = Schema.AccessMode.READ_ONLY,
    description = "The canonical identifier given to a thing.") id: String,
  @Schema(
    description =
      "The title or other short label of a work, including labels not present in the actual work or item but applied by the cataloguer for the purposes of search or description."
  ) title: Option[String],
  @Schema(
    `type` = "String",
    description =
      "The identifier used by researchers to cite or refer to a work."
  ) referenceNumber: Option[String] = None,
  @Schema(
    `type` = "List[Work]",
    description = "Ancestor works."
  ) partOf: Option[List[DisplayRelation]] = None,
  @Schema(
    `type` = "Integer",
    description = "Number of child works."
  ) totalParts: Int,
  @Schema(
    `type` = "Integer",
    description = "Number of descendent works."
  ) totalDescendentParts: Int,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Work"
)

object DisplayRelation {

  def apply(relation: Relation): DisplayRelation =
    DisplayRelation(
      id = relation.id.underlying,
      title = relation.title,
      referenceNumber = relation.collectionPath.flatMap(_.label),
      ontologyType = DisplayWork.displayWorkType(relation.workType),
      totalParts = relation.numChildren,
      totalDescendentParts = relation.numDescendents
    )
}
