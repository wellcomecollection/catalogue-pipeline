package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{Contributor, Id}

@Schema(
  name = "Contributor",
  description = "A contributor"
)
case class DisplayContributor(
  @Schema(description = "The agent.") agent: DisplayAbstractAgent,
  @Schema(description = "The list of contribution roles.") roles: List[
    DisplayContributionRole],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Contributor"
)

object DisplayContributor {
  def apply(contributor: Contributor[Id.Minted],
            includesIdentifiers: Boolean): DisplayContributor =
    DisplayContributor(
      agent = DisplayAbstractAgent(
        contributor.agent,
        includesIdentifiers = includesIdentifiers),
      roles = contributor.roles.map { DisplayContributionRole(_) }
    )
}
