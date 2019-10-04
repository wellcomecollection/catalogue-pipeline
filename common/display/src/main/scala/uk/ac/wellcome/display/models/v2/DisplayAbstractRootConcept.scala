package uk.ac.wellcome.display.models.v2

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import uk.ac.wellcome.models.work.internal._

@ApiModel(
  value = "Concept"
)
sealed trait DisplayAbstractRootConcept {
  val id: Option[String]
  val identifiers: Option[List[DisplayIdentifierV2]]
  val label: String
}

object DisplayAbstractRootConcept {
  def apply(abstractConcept: Displayable[AbstractRootConcept],
            includesIdentifiers: Boolean): DisplayAbstractRootConcept =
    abstractConcept match {
      // Horribleness to circumvent Java type erasure ಠ_ಠ
      case agentConcept @ Unidentifiable(_: AbstractAgent) =>
        DisplayAbstractAgentV2(
          agentConcept.asInstanceOf[Displayable[AbstractAgent]],
          includesIdentifiers)
      case agentConcept @ Identified(_: AbstractAgent, _, _, _) =>
        DisplayAbstractAgentV2(
          agentConcept.asInstanceOf[Displayable[AbstractAgent]],
          includesIdentifiers)
      case concept @ Unidentifiable(_: AbstractConcept) =>
        DisplayAbstractConcept(
          concept.asInstanceOf[Displayable[AbstractConcept]],
          includesIdentifiers)
      case concept @ Identified(_: AbstractConcept, _, _, _) =>
        DisplayAbstractConcept(
          concept.asInstanceOf[Displayable[AbstractConcept]],
          includesIdentifiers)
    }
}

@ApiModel(
  value = "Concept"
)
sealed trait DisplayAbstractConcept extends DisplayAbstractRootConcept

case object DisplayAbstractConcept {
  def apply(abstractConcept: Displayable[AbstractConcept],
            includesIdentifiers: Boolean): DisplayAbstractConcept =
    abstractConcept match {
      case Unidentifiable(concept: Concept) =>
        DisplayConcept(
          id = None,
          identifiers = None,
          label = concept.label
        )
      case Identified(
          concept: Concept,
          canonicalId,
          sourceIdentifier,
          otherIdentifiers) =>
        DisplayConcept(
          id = Some(canonicalId),
          identifiers =
            if (includesIdentifiers)
              Some(
                (sourceIdentifier +: otherIdentifiers).map(
                  DisplayIdentifierV2(_)))
            else None,
          label = concept.label
        )
      case Unidentifiable(period: Period) =>
        DisplayPeriod(
          id = None,
          identifiers = None,
          label = period.label
        )
      case Identified(
          period: Period,
          canonicalId,
          sourceIdentifier,
          otherIdentifiers) =>
        DisplayPeriod(
          id = Some(canonicalId),
          identifiers =
            if (includesIdentifiers)
              Some(
                (sourceIdentifier +: otherIdentifiers).map(
                  DisplayIdentifierV2(_)))
            else None,
          label = period.label
        )
      case Unidentifiable(place: Place) =>
        DisplayPlace(
          id = None,
          identifiers = None,
          label = place.label
        )
      case Identified(
          place: Place,
          canonicalId,
          sourceIdentifier,
          otherIdentifiers) =>
        DisplayPlace(
          id = Some(canonicalId),
          identifiers =
            if (includesIdentifiers)
              Some(
                (sourceIdentifier +: otherIdentifiers).map(
                  DisplayIdentifierV2(_)))
            else None,
          label = place.label
        )
    }
}

@ApiModel(
  value = "Concept",
  description = "A broad concept"
)
case class DisplayConcept(
  @ApiModelProperty(
    dataType = "String",
    value = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @ApiModelProperty(
    dataType = "String"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Concept"
) extends DisplayAbstractConcept

@ApiModel(
  value = "Period",
  description = "A period of time"
)
case class DisplayPeriod(
  @ApiModelProperty(
    dataType = "String",
    value = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @ApiModelProperty(
    dataType = "String"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Period"
) extends DisplayAbstractConcept

case object DisplayPeriod {
  def apply(period: Period): DisplayPeriod = DisplayPeriod(
    label = period.label
  )
}

@ApiModel(
  value = "Place",
  description = "A place"
)
case class DisplayPlace(
  @ApiModelProperty(
    dataType = "String",
    value = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @ApiModelProperty(
    dataType = "String"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Place"
) extends DisplayAbstractConcept

case object DisplayPlace {
  def apply(place: Place): DisplayPlace = DisplayPlace(
    label = place.label
  )
}

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
    canonicalId: Option[String] = None): DisplayAbstractAgentV2 =
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
