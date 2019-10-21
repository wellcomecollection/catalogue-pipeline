package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{
  AbstractAgent,
  Contributor,
  Displayable
}

@Schema(
  name = "Contributor",
  description = "A contributor"
)
case class DisplayContributor(
  @Schema(description = "The agent.") agent: DisplayAbstractAgentV2,
  @Schema(description = "The list of contribution roles.") roles: List[
    DisplayContributionRole],
  @JsonKey("type") ontologyType: String = "Contributor"
)

object DisplayContributor {
  def apply(contributor: Contributor[Displayable[AbstractAgent]],
            includesIdentifiers: Boolean): DisplayContributor =
    DisplayContributor(
      agent = DisplayAbstractAgentV2(
        contributor.agent,
        includesIdentifiers = includesIdentifiers),
      roles = contributor.roles.map { DisplayContributionRole(_) }
    )
}
