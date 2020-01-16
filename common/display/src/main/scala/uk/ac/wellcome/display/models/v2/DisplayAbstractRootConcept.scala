package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Concept"
)
sealed trait DisplayAbstractRootConcept {
  val id: Option[String]
  val identifiers: Option[List[DisplayIdentifierV2]]
  val label: String
}

object DisplayAbstractRootConcept {
  def apply(abstractConcept: AbstractRootConcept[Minted],
            includesIdentifiers: Boolean): DisplayAbstractRootConcept =
    abstractConcept match {
      case agent: AbstractAgent[Minted] =>
        DisplayAbstractAgentV2(agent, includesIdentifiers)
      case concept: AbstractConcept[Minted] =>
        DisplayAbstractConcept(concept, includesIdentifiers)
    }
}

@Schema(
  name = "Concept"
)
sealed trait DisplayAbstractConcept extends DisplayAbstractRootConcept

case object DisplayAbstractConcept extends GetIdentifiers {

  def apply(abstractConcept: AbstractConcept[Minted],
            includesIdentifiers: Boolean): DisplayAbstractConcept =
    abstractConcept match {
      case Concept(idState, label) =>
        DisplayConcept(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers)
        )
      case Period(idState, label, range) =>
        DisplayPeriod(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers)
        )
      case Place(idState, label) =>
        DisplayPlace(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers)
        )
    }
}

@Schema(
  name = "Concept",
  description = "A broad concept"
)
case class DisplayConcept(
  @Schema(
    `type` = "String",
    description = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Concept"
) extends DisplayAbstractConcept

@Schema(
  name = "Period",
  description = "A period of time"
)
case class DisplayPeriod(
  @Schema(
    `type` = "String",
    description = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Period"
) extends DisplayAbstractConcept

case object DisplayPeriod {
  def apply(period: Period[Minted]): DisplayPeriod = DisplayPeriod(
    label = period.label
  )
}

@Schema(
  name = "Place",
  description = "A place"
)
case class DisplayPlace(
  @Schema(
    `type` = "String",
    description = "The canonical identifier given to a thing"
  ) id: Option[String] = None,
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Place"
) extends DisplayAbstractConcept

case object DisplayPlace {
  def apply(place: Place[Minted]): DisplayPlace = DisplayPlace(
    label = place.label
  )
}

@Schema(
  name = "Agent"
)
sealed trait DisplayAbstractAgentV2 extends DisplayAbstractRootConcept

case object DisplayAbstractAgentV2 extends GetIdentifiers {

  def apply(agent: AbstractAgent[Minted],
            includesIdentifiers: Boolean): DisplayAbstractAgentV2 =
    agent match {
      case Agent(idState, label) =>
        DisplayAgentV2(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers),
        )
      case Person(idState, label, prefix, numeration) =>
        DisplayPersonV2(
          id = idState.id,
          label = label,
          numeration = numeration,
          prefix = prefix,
          identifiers = getIdentifiers(idState, includesIdentifiers)
        )
      case Organisation(idState, label) =>
        DisplayOrganisationV2(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers),
        )
      case Meeting(idState, label) =>
        DisplayMeetingV2(
          id = idState.id,
          label = label,
          identifiers = getIdentifiers(idState, includesIdentifiers),
        )
    }
}

@Schema(
  name = "Agent"
)
case class DisplayAgentV2(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifierV2]],
  @Schema(
    description = "The name of the agent"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Agent"
) extends DisplayAbstractAgentV2

@Schema(
  name = "Person"
)
case class DisplayPersonV2(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @Schema(
    description = "The name of the person"
  ) label: String,
  @Schema(
    `type` = "String",
    description = "The title of the person"
  ) prefix: Option[String] = None,
  @Schema(
    `type` = "String",
    description = "The numeration of the person"
  ) numeration: Option[String] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Person")
    extends DisplayAbstractAgentV2

@Schema(
  name = "Organisation"
)
case class DisplayOrganisationV2(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @Schema(
    description = "The name of the organisation"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Organisation")
    extends DisplayAbstractAgentV2

case class DisplayMeetingV2(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]],
  @Schema(
    description = "The name of the meeting"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Meeting")
    extends DisplayAbstractAgentV2

trait GetIdentifiers {

  protected def getIdentifiers(idState: IdState, includesIdentifiers: Boolean) =
    if (includesIdentifiers)
      Option(idState.otherIds.map(DisplayIdentifierV2(_)))
        .filter(_.nonEmpty)
    else
      None
}
