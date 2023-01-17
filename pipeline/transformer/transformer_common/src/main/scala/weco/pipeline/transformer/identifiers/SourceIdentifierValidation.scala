package weco.pipeline.transformer.identifiers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}

import java.util.UUID
import scala.Function.const
import scala.util.Try
import scala.util.matching.Regex

object SourceIdentifierValidation {
  implicit class SourceIdentifierOps(sourceIdentifier: SourceIdentifier)
      extends Logging {
    import IdentifierRegexes._

    def validated: Option[SourceIdentifier] =
      if (isValid) {
        Some(sourceIdentifier)
      } else {
        None
      }

    def validatedWithWarning: Option[SourceIdentifier] = validated match {
      case None =>
        warn(
          s"Invalid ${sourceIdentifier.identifierType.id}: ${sourceIdentifier.value}"
        )
        None
      case some => some
    }

    private def isValid: Boolean = {
      val predicate: String => Boolean = sourceIdentifier.identifierType match {
        case IdentifierType.MiroImageNumber      => miroImageNumber.toPredicate
        case IdentifierType.SierraSystemNumber   => sierraSystemNumber.toPredicate
        case IdentifierType.SierraIdentifier     => sierraIdentifier.toPredicate
        case IdentifierType.CalmRefNo            => calmRefNo.toPredicate
        case IdentifierType.CalmAltRefNo         => calmRefNo.toPredicate
        case IdentifierType.CalmRecordIdentifier => tryParseUUID(_).isSuccess
        case IdentifierType.METS                 => sierraSystemNumber.toPredicate
        case IdentifierType.WellcomeDigcode      => wellcomeDigcode.toPredicate
        case IdentifierType.IconographicNumber   => iconographicNumber.toPredicate
        // For other identifier types, we don't do validation
        case _ => const(true)
      }
      predicate(sourceIdentifier.value)
    }

    /*
     * Calm IDs follow the UUID spec
     *
     * e.g: f5217b45-b742-472b-95c3-f136d5de1104
     * see: `https://search.wellcomelibrary.org/iii/encore/record/C__Rb1971204?marcData=Y`
     */
    private def tryParseUUID(str: String): Try[UUID] = Try(UUID.fromString(str))

    private implicit class RegexOps(regex: Regex) {
      def toPredicate: String => Boolean = regex.findFirstIn(_).isDefined
    }
  }

}
