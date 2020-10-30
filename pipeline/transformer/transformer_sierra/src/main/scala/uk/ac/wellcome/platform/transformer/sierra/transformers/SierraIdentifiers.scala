package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

// Populate wwork:identifiers.
//
//    We populate with the following identifiers:
//
//    -   "sierra-identifier" for the 7-digit internal ID
//
//    -   "isbn" from MARC tag 020 subfield $a.  This is repeatable.
//        https://www.loc.gov/marc/bibliographic/bd020.html
//
//    -   "issn" from MARC tag 022 ǂa.  This is repeatable.
//        https://www.loc.gov/marc/bibliographic/bd022.html
//
//    -   "wellcome-digcode" from MARC tag 759 ǂa.  This is repeatable.
//        Note: MARC 759 is not assigned by the Library of Congress.
//
object SierraIdentifiers extends SierraDataTransformer with SierraQueryOps {

  type Output = List[SourceIdentifier]

  def apply(bibId: SierraBibNumber,
            bibData: SierraBibData): List[SourceIdentifier] = {
    val sierraIdentifier = SourceIdentifier(
      identifierType = IdentifierType("sierra-identifier"),
      ontologyType = "Work",
      value = bibId.withoutCheckDigit
    )

    List(sierraIdentifier) ++ getIsbnIdentifiers(bibData) ++ getIssnIdentifiers(
      bibData) ++ getDigcodes(bibData)
  }

  // Find ISBN (International Serial Book Number) identifiers from MARC 020 ǂa.
  private def getIsbnIdentifiers(
    bibData: SierraBibData): List[SourceIdentifier] =
    bibData
      .subfieldsWithTag("020" -> "a")
      .contents
      .distinct
      .map { value =>
        SourceIdentifier(
          identifierType = IdentifierType("isbn"),
          ontologyType = "Work",
          value = value
        )
      }

  // Find ISSN (International Standard Serial Number) identifiers from MARC 022 ǂa.
  private def getIssnIdentifiers(
    bibData: SierraBibData): List[SourceIdentifier] =
    bibData
      .subfieldsWithTag("022" -> "a")
      .contents
      .distinct
      .map { value =>
        SourceIdentifier(
          identifierType = IdentifierType("issn"),
          ontologyType = "Work",
          value = value
        )
      }

  // Find the digcodes from MARC 759 ǂa.
  //
  // A digcode is a Wellcome-specific identifier that identifies the
  // digitisation project under which the item was digitised.  These are
  // used by staff to quickly locate, for example, all the MOH reports or
  // everything digitised from a partner institution.
  //
  // The value of the digcode should only be the contiguous alphabetic
  // string that starts with `dig`.
  private def getDigcodes(bibData: SierraBibData): List[SourceIdentifier] = {
    val marcValues =
      bibData
        .subfieldsWithTag("759" -> "a")
        .contents

    // Capture any string starting with `dig` followed by a non-zero number
    // of alphabet characters.  The digcode is only useful if it identifies
    // a digitisation project, hence requiring a non-empty suffix.
    //
    // We match any number of characters after the alphabetic string, so the
    // pattern match below captures (but discards) extra data.
    //
    // e.g. `digmoh(Channel)` becomes `digmoh`
    val digcodeRegex = "^(dig[a-z]+).*$".r

    val digcodeValues =
      marcValues
        .collect {
          case digcodeRegex(d) => d
        }

    digcodeValues.distinct
      .map { value =>
        SourceIdentifier(
          identifierType = IdentifierType("wellcome-digcode"),
          ontologyType = "Work",
          value = value
        )
      }
  }
}
