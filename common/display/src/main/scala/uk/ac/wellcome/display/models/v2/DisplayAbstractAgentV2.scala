package uk.ac.wellcome.display.models.v2

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import uk.ac.wellcome.models.work.internal._

@ApiModel(
  value = "Agent"
)
sealed trait DisplayAbstractAgentV2 extends DisplayAbstractRootConcept

case object DisplayAbstractAgentV2 {

  def apply(displayableAgent: Displayable[AbstractAgent],
            includesIdentifiers: Boolean): DisplayAbstractAgentV2 =
    displayableAgent match {
      case Unidentifiable(agent) => displayAgent(agent)
      case Identified(agent, canonicalId, sourceId, otherIds) =>
        displayAgent(
          agent,
          if (includesIdentifiers)
            Some((sourceId +: otherIds).map(DisplayIdentifierV2(_)))
          else
            None,
          Some(canonicalId)
        )
    }

  private def displayAgent(
    agent: AbstractAgent,
    displayIdentifiers: Option[List[DisplayIdentifierV2]] = None,
    canonicalId:  Option[String] = None):  DisplayAbstractAgentV2 =
    agent match {
      case Agent(label) =>
        DisplayAgentV2(canonicalId, displayIdentifiers, label)
      case Person(label, prefix, numeration) =>
        DisplayPersonV2(
          canonicalId,
          displayIdentifiers,
          label,
          prefix,
          numeration
        )
      case Organisation(label) =>
        DisplayOrganisationV2(canonicalId, displayIdentifiers, label)
      case Meeting(label) =>
        DisplayMeetingV2(canonicalId, displayIdentifiers, label)
    }
}

@ApiModel(
  value = "Agent"
)
case class DisplayAgentV2(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifierV2]],
  @ApiModelProperty(
    value = "The name of the agent"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Agent"
) extends DisplayAbstractAgentV2

@ApiModel(
  value = "Person"
)
case class DisplayPersonV2(
  @ApiModelProperty(
    dataType = "String",
    readOnly = true,
    value = "The canonical identifier given to a thing.") id: Option[String],
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @ApiModelProperty(
    value = "The name of the person"
  ) label: String,
  @ApiModelProperty(
    dataType = "String",
    value = "The title of the person"
  ) prefix: Option[String] = None,
  @ApiModelProperty(
    dataType = "String",
    value = "The numeration of the person"
  ) numeration: Option[String] = None,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Person")
    extends DisplayAbstractAgentV2

@ApiModel(
  value = "Organisation"
)
case class DisplayOrganisationV2(
  @ApiModelProperty(
    dataType = "String",
    readOnly = true,
    value = "The canonical identifier given to a thing.") id: Option[String],
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @ApiModelProperty(
    value = "The name of the organisation"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Organisation")
    extends DisplayAbstractAgentV2

case class DisplayMeetingV2(
  @ApiModelProperty(
    dataType = "String",
    readOnly = true,
    value = "The canonical identifier given to a thing.") id: Option[String],
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @ApiModelProperty(
    value = "The name of the meeting"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Meeting")
    extends DisplayAbstractAgentV2
