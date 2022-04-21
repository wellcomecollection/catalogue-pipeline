package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.ContributionRole

case class DisplayContributionRole(
  label: String,
  @JsonKey("type") ontologyType: String = "ContributionRole"
)

object DisplayContributionRole {
  def apply(contributionRole: ContributionRole): DisplayContributionRole =
    DisplayContributionRole(
      label = contributionRole.label
    )
}
