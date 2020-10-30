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
object SierraIdentifiers extends SierraDataTransformer with SierraQueryOps {

  type Output = List[SourceIdentifier]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): List[SourceIdentifier] = {
    val sierraIdentifier = SourceIdentifier(
      identifierType = IdentifierType("sierra-identifier"),
      ontologyType = "Work",
      value = bibId.withoutCheckDigit
    )

    List(sierraIdentifier) ++ getIsbnIdentifiers(bibData) ++ getIssnIdentifiers(bibData)
  }

  // Find ISBN (International Serial Book Number) identifiers from MARC 020 ǂa.
  private def getIsbnIdentifiers(bibData: SierraBibData): List[SourceIdentifier] =
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
  private def getIssnIdentifiers(bibData: SierraBibData): List[SourceIdentifier] =
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
}
