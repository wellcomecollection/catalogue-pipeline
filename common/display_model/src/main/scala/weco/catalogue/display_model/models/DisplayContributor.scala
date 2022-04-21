package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Contributor

case class DisplayContributor(
  agent: DisplayAbstractAgent,
  roles: List[DisplayContributionRole],
  @JsonKey("type") ontologyType: String = "Contributor"
)

object DisplayContributor {
  def apply(
    contributor: Contributor[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayContributor =
    DisplayContributor(
      agent = DisplayAbstractAgent(
        contributor.agent,
        includesIdentifiers = includesIdentifiers
      ),
      roles = contributor.roles.map { DisplayContributionRole(_) }
    )
}
