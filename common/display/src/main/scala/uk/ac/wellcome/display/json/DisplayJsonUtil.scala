package uk.ac.wellcome.display.json

import io.circe.generic.extras.{AutoDerivation, Configuration}
import io.circe.{Encoder, Printer}
import io.circe.syntax._

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
  private val printer = Printer.noSpaces.copy(
    dropNullValues = true
  )

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  def toJson[T](value: T)(implicit encoder: Encoder[T]): String = {
    assert(encoder != null)
    printer.pretty(value.asJson)
  }
}

object DisplayJsonUtil extends DisplayJsonUtil
