package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroIdentifiers extends MiroTransformableUtils {
  def getOtherIdentifiers(miroRecord: MiroRecord): List[SourceIdentifier] = {

    // Add the Sierra system number from the INNOPAC ID, if it's present.
    //
    // We add a b-prefix because everything in Miro is a bibliographic record,
    // but there are other types in Sierra (e.g. item, holding) with matching
    // IDs but different prefixes.

    // First, a note: one of the Miro records has a bit of weird data in
    // this fields; specifically:
    //
    //    'L 35411 \n\n15551040'
    //
    // We fix that here, for this record only, so the change is documented.
    //
    val innopacIdField = if (miroRecord.imageNumber == "L0035411") {
      miroRecord.innopacID.map { _.replaceAll("L 35411 \n\n", "") }
    } else {
      miroRecord.innopacID
    }

    val sierraList: List[SourceIdentifier] = innopacIdField match {
      case Some(s) => {

        // The ID in the Miro record is an 8-digit number with a check digit
        // (which may be x).  The format we use for Sierra system numbers
        // is a record type prefix ("b") and 8-digits.
        //
        // Regex explanation:
        //
        //    ^                 start of string
        //    (?:\.?[bB])?      non-capturing group, which trims 'b' or 'B'
        //                      or '.b' or '.B' from the start of the string,
        //                      but *not* a lone '.'
        //    ([0-9]{7}[0-9xX]) capturing group, the 8 digits of the system
        //                      number plus the final check digit, which may
        //                      be an X
        //    $                 end of the string
        //
        val regexMatch = """^(?:\.?[bB])?([0-9]{7}[0-9xX])$""".r.unapplySeq(s)
        regexMatch match {
          case Some(s) =>
            s.flatMap {
              id =>
                SourceIdentifier(
                  identifierType = IdentifierType.SierraSystemNumber,
                  ontologyType = "Work",
                  value = s"b$id"
                ).validatedWithWarning
            }
          case _ =>
            throw new RuntimeException(
              s"Expected 8-digit INNOPAC ID or nothing, got ${miroRecord.innopacID}"
            )
        }
      }
      case None => List()
    }

    val libraryRefsList: List[SourceIdentifier] =
      zipMiroFields(
        keys = miroRecord.libraryRefDepartment,
        values = miroRecord.libraryRefId
      ).distinct
        .collect {
          case (Some(label), Some(value)) =>
            Option(label)
              .flatMap {
                // We have an identifier type for iconographic numbers (e.g. 577895i),
                // so use that when possible.
                //
                // Note that the "Iconographic Collection" identifiers have a lot of
                // other stuff which isn't an i-number, so we should be careful what
                // we put here - so we make sure to validate the SourceIdentifier.
                case "Iconographic Collection" =>
                  SourceIdentifier(
                    identifierType = IdentifierType.IconographicNumber,
                    ontologyType = "Work",
                    value = value
                  ).validated
                case _ => None
              }
              .getOrElse(
                // Put any other identifiers in one catch-all scheme until we come
                // up with a better way to handle them.  We want them visible and
                // searchable, but they're not worth spending more time on right now.
                SourceIdentifier(
                  identifierType = IdentifierType.MiroLibraryReference,
                  ontologyType = "Work",
                  value = s"$label $value"
                )
              )
        }
    sierraList ++ libraryRefsList
  }
}
