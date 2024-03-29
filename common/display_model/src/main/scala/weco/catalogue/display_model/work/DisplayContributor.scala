package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Contributor

case class DisplayContributor(
  agent: DisplayAbstractRootConcept,
  roles: List[DisplayContributionRole],
  primary: Boolean,
  @JsonKey("type") ontologyType: String = "Contributor"
)

object DisplayContributor {
  def apply(
    contributor: Contributor[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayContributor =
    DisplayContributor(
      agent = DisplayAbstractRootConcept(
        contributor.agent,
        includesIdentifiers = includesIdentifiers
      ),
      roles = contributor.roles.map { DisplayContributionRole(_) },
      primary = contributor.primary
    )
}
