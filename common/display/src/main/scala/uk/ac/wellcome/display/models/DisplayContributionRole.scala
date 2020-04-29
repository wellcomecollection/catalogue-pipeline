package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.ContributionRole

@Schema(
  name = "ContributionRole",
  description = "A contribution role"
)
case class DisplayContributionRole(
  @Schema(
    name = "The name of the agent"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "ContributionRole"
)

object DisplayContributionRole {
  def apply(contributionRole: ContributionRole): DisplayContributionRole =
    DisplayContributionRole(
      label = contributionRole.label
    )
}
