package weco.catalogue.display_model

import io.circe.Encoder
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations._
import weco.catalogue.display_model.work._
import weco.http.json.DisplayJsonUtil._

object Implicits {

  // Circe wants to add a type discriminator, and we don't want it to!  Doing so
  // would expose internal names like "DisplayDigitalLocationV1" in the public JSON.
  // So instead we have to do the slightly less nice thing of encoding all the subclasses
  // here by hand.  Annoying, it is.
  //
  // It is the "recommended: approach in the Circe docs:
  // https://circe.github.io/circe/codecs/adt.html

  implicit val abstractAgentEncoder: Encoder[DisplayAbstractAgent] = {
    case agent: DisplayAgent               => agent.asJson
    case person: DisplayPerson             => person.asJson
    case organisation: DisplayOrganisation => organisation.asJson
    case meeting: DisplayMeeting           => meeting.asJson
  }

  implicit val abstractConceptEncoder: Encoder[DisplayAbstractConcept] = {
    case concept: DisplayConcept    => concept.asJson
    case place: DisplayPlace        => place.asJson
    case period: DisplayPeriod      => period.asJson
    case genre: DisplayGenreConcept => genre.asJson
  }

  implicit val abstractRootConceptEncoder
    : Encoder[DisplayAbstractRootConcept] = {
    case agent: DisplayAbstractAgent     => agent.asJson
    case concept: DisplayAbstractConcept => concept.asJson
  }

  implicit val locationEncoder: Encoder[DisplayLocation] = {
    case digitalLocation: DisplayDigitalLocation =>
      digitalLocation.asJson
    case physicalLocation: DisplayPhysicalLocation =>
      physicalLocation.asJson
  }

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _encDisplayAccessCondition: Encoder[DisplayAccessCondition] =
    deriveConfiguredEncoder
  implicit val _encDisplayLanguage: Encoder[DisplayLanguage] =
    deriveConfiguredEncoder
  implicit val _encDisplayFormat: Encoder[DisplayFormat] =
    deriveConfiguredEncoder
  implicit val _encDisplayPeriod: Encoder[DisplayPeriod] =
    deriveConfiguredEncoder
  implicit val _encDisplayContributor: Encoder[DisplayContributor] =
    deriveConfiguredEncoder
  implicit val _encDisplayIdentifier: Encoder[DisplayIdentifier] =
    deriveConfiguredEncoder
  implicit val _encDisplaySubject: Encoder[DisplaySubject] =
    deriveConfiguredEncoder
  implicit val _encDisplayGenre: Encoder[DisplayGenre] = deriveConfiguredEncoder
  implicit val _encDisplayProductionEvent: Encoder[DisplayProductionEvent] =
    deriveConfiguredEncoder
  implicit val _encDisplayImage: Encoder[DisplayImage] = deriveConfiguredEncoder

  implicit val _encDisplayLicense: Encoder[DisplayLicense] =
    deriveConfiguredEncoder
}
