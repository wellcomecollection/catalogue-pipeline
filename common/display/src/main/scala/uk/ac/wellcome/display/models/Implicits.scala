package uk.ac.wellcome.display.models

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.display.json.DisplayJsonUtil
import uk.ac.wellcome.display.models.v2._
import io.circe.syntax._

object Implicits extends DisplayJsonUtil {

  // Circe wants to add a type discriminator, and we don't want it to!  Doing so
  // would expose internal names like "DisplayDigitalLocationV1" in the public JSON.
  // So instead we have to do the slightly less nice thing of encoding all the subclasses
  // here by hand.  Annoying, it is.
  //
  // It is the "recommended: approach in the Circe docs:
  // https://circe.github.io/circe/codecs/adt.html

  implicit val abstractAgentEncoder: Encoder[DisplayAbstractAgentV2] = {
    case agent: DisplayAgentV2               => agent.asJson
    case person: DisplayPersonV2             => person.asJson
    case organisation: DisplayOrganisationV2 => organisation.asJson
    case meeting: DisplayMeetingV2           => meeting.asJson
  }

  implicit val abstractConceptEncoder: Encoder[DisplayAbstractConcept] = {
    case concept: DisplayConcept => concept.asJson
    case place: DisplayPlace     => place.asJson
    case period: DisplayPeriod   => period.asJson
  }

  implicit val abstractRootConceptEncoder
    : Encoder[DisplayAbstractRootConcept] = {
    case agent: DisplayAbstractAgentV2   => agent.asJson
    case concept: DisplayAbstractConcept => concept.asJson
  }

  implicit val locationV2Encoder: Encoder[DisplayLocationV2] = {
    case digitalLocation: DisplayDigitalLocationV2   => digitalLocation.asJson
    case physicalLocation: DisplayPhysicalLocationV2 => physicalLocation.asJson
  }

  implicit val displayWorkEncoder: Encoder[DisplayWork] = {
    case work: DisplayWorkV2 => work.asJson
  }

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _enc01: Encoder[DisplayLanguage] = deriveEncoder
  implicit val _enc02: Encoder[DisplayWorkType] = deriveEncoder
  implicit val _enc03: Encoder[DisplayPeriod] = deriveEncoder
  implicit val _enc04: Encoder[DisplayContributor] = deriveEncoder
  implicit val _enc05: Encoder[DisplayIdentifierV2] = deriveEncoder
  implicit val _enc06: Encoder[DisplaySubject] = deriveEncoder
  implicit val _enc07: Encoder[DisplayGenre] = deriveEncoder
  implicit val _enc08: Encoder[DisplayProductionEvent] = deriveEncoder
  implicit val _enc09: Encoder[DisplayItemV2] = deriveEncoder
  implicit val _enc10: Encoder[DisplayNote] = deriveEncoder
  implicit val _enc11: Encoder[DisplayWorkV2] = deriveEncoder

  implicit val _dec01: Decoder[DisplayLanguage] = deriveDecoder
  implicit val _dec02: Decoder[DisplayWorkType] = deriveDecoder
  implicit val _dec03: Decoder[DisplayPeriod] = deriveDecoder
  implicit val _dec04: Decoder[DisplayContributor] = deriveDecoder
  implicit val _dec05: Decoder[DisplayIdentifierV2] = deriveDecoder
  implicit val _dec06: Decoder[DisplaySubject] = deriveDecoder
  implicit val _dec07: Decoder[DisplayGenre] = deriveDecoder
  implicit val _dec08: Decoder[DisplayProductionEvent] = deriveDecoder
  implicit val _dec09: Decoder[DisplayItemV2] = deriveDecoder
  implicit val _dec10: Decoder[DisplayNote] = deriveDecoder
  implicit val _dec11: Decoder[DisplayWorkV2] = deriveDecoder
}
