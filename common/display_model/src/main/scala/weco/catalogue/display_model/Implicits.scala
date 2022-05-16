package weco.catalogue.display_model

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder, HCursor}
import io.circe.syntax._
import weco.http.json.DisplayJsonUtil._
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations._
import weco.catalogue.display_model.work._

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
    case concept: DisplayConcept => concept.asJson
    case place: DisplayPlace     => place.asJson
    case period: DisplayPeriod   => period.asJson
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

  implicit val locationDecoder: Decoder[DisplayLocation] =
    (c: HCursor) =>
      for {
        ontologyType <- c.downField("type").as[String]

        location <- ontologyType match {
          case "PhysicalLocation" => c.as[DisplayPhysicalLocation]
          case "DigitalLocation"  => c.as[DisplayDigitalLocation]
          case _ =>
            throw new IllegalArgumentException(
              s"Unexpected location type: $ontologyType"
            )
        }
      } yield location

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _enc00: Encoder[DisplayAccessCondition] = deriveConfiguredEncoder
  implicit val _enc01: Encoder[DisplayLanguage] = deriveConfiguredEncoder
  implicit val _enc02: Encoder[DisplayFormat] = deriveConfiguredEncoder
  implicit val _enc03: Encoder[DisplayPeriod] = deriveConfiguredEncoder
  implicit val _enc04: Encoder[DisplayContributor] = deriveConfiguredEncoder
  implicit val _enc05: Encoder[DisplayIdentifier] = deriveConfiguredEncoder
  implicit val _enc06: Encoder[DisplaySubject] = deriveConfiguredEncoder
  implicit val _enc07: Encoder[DisplayGenre] = deriveConfiguredEncoder
  implicit val _enc08: Encoder[DisplayProductionEvent] = deriveConfiguredEncoder
  implicit val _enc09: Encoder[DisplayItem] = deriveConfiguredEncoder
  implicit val _enc10: Encoder[DisplayNote] = deriveConfiguredEncoder
  implicit val _enc11: Encoder[DisplayWork] = deriveConfiguredEncoder
  implicit val _enc12: Encoder[DisplayImage] = deriveConfiguredEncoder

  implicit val _encDisplayLicense: Encoder[DisplayLicense] =
    deriveConfiguredEncoder

  implicit val _encDisplayAvailability: Encoder[DisplayAvailability] =
    deriveConfiguredEncoder

  implicit val _dec00: Decoder[DisplayAccessCondition] = deriveConfiguredDecoder
  implicit val _dec01: Decoder[DisplayLanguage] = deriveConfiguredDecoder
  implicit val _dec02: Decoder[DisplayFormat] = deriveConfiguredDecoder
  implicit val _dec03: Decoder[DisplayPeriod] = deriveConfiguredDecoder
  implicit val _dec04: Decoder[DisplayContributor] = deriveConfiguredDecoder
  implicit val _dec05: Decoder[DisplayIdentifier] = deriveConfiguredDecoder
  implicit val _dec06: Decoder[DisplaySubject] = deriveConfiguredDecoder
  implicit val _dec07: Decoder[DisplayGenre] = deriveConfiguredDecoder
  implicit val _dec08: Decoder[DisplayProductionEvent] = deriveConfiguredDecoder
  implicit val _dec09: Decoder[DisplayItem] = deriveConfiguredDecoder
  implicit val _dec10: Decoder[DisplayNote] = deriveConfiguredDecoder
  implicit val _dec11: Decoder[DisplayWork] = deriveConfiguredDecoder
  implicit val _dec12: Decoder[DisplayImage] = deriveConfiguredDecoder
}
