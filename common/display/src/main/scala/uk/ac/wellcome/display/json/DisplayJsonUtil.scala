package uk.ac.wellcome.display.json

import io.circe.generic.extras.{AutoDerivation, Configuration}
import io.circe.{Encoder, Printer}
import io.circe.syntax._
import uk.ac.wellcome.display.models._

/** Format JSON objects as suitable for display.
  *
  * This is different from `JsonUtil` for a couple of reasons:
  *
  *   - We enable autoderivation for a smaller number of types.  We should never
  *     see `URL` or `UUID` in our display models, so we don't have encoders here.
  *   - We omit null values, but display empty lists.  For internal apps, we always
  *     render the complete value for disambiguation.
  *
  */
trait DisplayJsonUtil extends AutoDerivation {
  val printer = Printer.noSpaces.copy(
    dropNullValues = true
  )

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  def toJson[T](value: T)(implicit encoder: Encoder[T]): String = {
    assert(encoder != null)
    printer.print(value.asJson)
  }

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
    case digitalLocation: DisplayDigitalLocation   => digitalLocation.asJson
    case physicalLocation: DisplayPhysicalLocation => physicalLocation.asJson
  }
}

object DisplayJsonUtil extends DisplayJsonUtil
