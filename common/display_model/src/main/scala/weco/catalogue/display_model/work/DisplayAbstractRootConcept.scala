package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.identifiers.{
  DisplayIdentifier,
  GetIdentifiers
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._

sealed trait DisplayAbstractRootConcept {
  val id: Option[String]
  val identifiers: Option[List[DisplayIdentifier]]
  val label: String
}

object DisplayAbstractRootConcept {
  def apply(
    abstractConcept: AbstractRootConcept[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayAbstractRootConcept =
    abstractConcept match {
      case agent: AbstractAgent[IdState.Minted] =>
        DisplayAbstractAgent(agent, includesIdentifiers)
      case concept: AbstractConcept[IdState.Minted] =>
        DisplayAbstractConcept(concept, includesIdentifiers)
    }
}

sealed trait DisplayAbstractConcept extends DisplayAbstractRootConcept

case object DisplayAbstractConcept extends GetIdentifiers {

  def apply(
    abstractConcept: AbstractConcept[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayAbstractConcept =
    abstractConcept match {
      case Concept(id, label) =>
        DisplayConcept(
          id = id.maybeCanonicalId.map {
            _.underlying
          },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case GenreConcept(id, label) =>
        DisplayGenreConcept(
          id = id.maybeCanonicalId.map {
            _.underlying
          },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Period(id, label, _) =>
        DisplayPeriod(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Place(id, label) =>
        DisplayPlace(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
    }
}

case class DisplayConcept(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  label: String,
  @JsonKey("type") ontologyType: String = "Concept"
) extends DisplayAbstractConcept

case class DisplayGenreConcept(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  label: String,
  @JsonKey("type") ontologyType: String = "Genre"
) extends DisplayAbstractConcept

case class DisplayPeriod(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  label: String,
  @JsonKey("type") ontologyType: String = "Period"
) extends DisplayAbstractConcept

case object DisplayPeriod {
  def apply(period: Period[IdState.Minted]): DisplayPeriod = DisplayPeriod(
    label = period.label
  )
}

case class DisplayPlace(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  label: String,
  @JsonKey("type") ontologyType: String = "Place"
) extends DisplayAbstractConcept

case object DisplayPlace {
  def apply(place: Place[IdState.Minted]): DisplayPlace = DisplayPlace(
    label = place.label
  )
}

sealed trait DisplayAbstractAgent extends DisplayAbstractRootConcept

case object DisplayAbstractAgent extends GetIdentifiers {

  def apply(
    agent: AbstractAgent[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplayAbstractAgent =
    agent match {
      case Agent(id, label) =>
        DisplayAgent(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Person(id, label, prefix, numeration) =>
        DisplayPerson(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          numeration = numeration,
          prefix = prefix,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Organisation(id, label) =>
        DisplayOrganisation(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
      case Meeting(id, label) =>
        DisplayMeeting(
          id = id.maybeCanonicalId.map { _.underlying },
          label = label,
          identifiers = getIdentifiers(id, includesIdentifiers)
        )
    }
}

case class DisplayAgent(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifier]],
  label: String,
  @JsonKey("type") ontologyType: String = "Agent"
) extends DisplayAbstractAgent

case class DisplayPerson(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifier]],
  label: String,
  prefix: Option[String] = None,
  numeration: Option[String] = None,
  @JsonKey("type") ontologyType: String = "Person"
) extends DisplayAbstractAgent

case class DisplayOrganisation(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifier]],
  label: String,
  @JsonKey("type") ontologyType: String = "Organisation"
) extends DisplayAbstractAgent

case class DisplayMeeting(
  id: Option[String],
  identifiers: Option[List[DisplayIdentifier]],
  label: String,
  @JsonKey("type") ontologyType: String = "Meeting"
) extends DisplayAbstractAgent
