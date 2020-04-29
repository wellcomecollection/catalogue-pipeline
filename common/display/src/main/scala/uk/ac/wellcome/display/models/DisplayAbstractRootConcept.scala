package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Concept"
)
sealed trait DisplayAbstractRootConcept {
  val id: Option[String]
  val identifiers: Option[List[DisplayIdentifier]]
  val label: String
}

object DisplayAbstractRootConcept {
  def apply(abstractConcept: AbstractRootConcept[Minted],
            includesIdentifiers: Boolean): DisplayAbstractRootConcept =
    abstractConcept match {
      case agent: AbstractAgent[Minted] =>
        DisplayAbstractAgent(agent, includesIdentifiers)
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
      case Concept(id, label) =>
        DisplayConcept(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Period(id, label, range) =>
        DisplayPeriod(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Place(id, label) =>
        DisplayPlace(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
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
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]] = None,
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
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]] = None,
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
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]] = None,
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
sealed trait DisplayAbstractAgent extends DisplayAbstractRootConcept

case object DisplayAbstractAgent extends GetIdentifiers {

  def apply(agent: AbstractAgent[Minted],
            includesIdentifiers: Boolean): DisplayAbstractAgent =
    agent match {
      case Agent(id, label) =>
        DisplayAgent(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers),
        )
      case Person(id, label, prefix, numeration) =>
        DisplayPerson(
          id = id.maybeCanonicalId,
          label = label,
          numeration = numeration,
          prefix = prefix,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Organisation(id, label) =>
        DisplayOrganisation(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers),
        )
      case Meeting(id, label) =>
        DisplayMeeting(
          id = id.maybeCanonicalId,
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers),
        )
    }
}

@Schema(
  name = "Agent"
)
case class DisplayAgent(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifier]],
  @Schema(
    description = "The name of the agent"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Agent"
) extends DisplayAbstractAgent

@Schema(
  name = "Person"
)
case class DisplayPerson(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]],
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
    extends DisplayAbstractAgent

@Schema(
  name = "Organisation"
)
case class DisplayOrganisation(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]],
  @Schema(
    description = "The name of the organisation"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Organisation")
    extends DisplayAbstractAgent

case class DisplayMeeting(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]],
  @Schema(
    description = "The name of the meeting"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Meeting")
    extends DisplayAbstractAgent

trait GetIdentifiers {

  protected def getIdentifiers(id: IdState, includesIdentifiers: Boolean) =
    if (includesIdentifiers)
      Option(id.allSourceIdentifiers.map(DisplayIdentifier(_)))
        .filter(_.nonEmpty)
    else
      None
}
