package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.Subfield

import scala.util.matching.Regex

object SierraIdentifiers
    extends SierraIdentifiedDataTransformer
    with SierraQueryOps {

  type Output = List[SourceIdentifier]

  def apply(
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): List[SourceIdentifier] =
    createSierraIdentifier(bibId) ++
      getIsbnIdentifiers(bibData) ++
      getIssnIdentifiers(bibData) ++
      getDigcodes(bibData) ++
      getIconographicNumbers(bibData) ++
      getEstcReferences(bibData)

  /** Create a seven-digit ID based on the internal ID.
    *
    * We use the eight-digit ID with check digit as the sourceIdentifier on the
    * Work.
    */
  private def createSierraIdentifier(
    bibId: SierraBibNumber
  ): List[SourceIdentifier] =
    List(
      SourceIdentifier(
        identifierType = IdentifierType.SierraIdentifier,
        ontologyType = "Work",
        value = bibId.withoutCheckDigit
      )
    )

  /** Find ISBN (International Serial Book Number) identifiers from MARC 020 ǂa.
    *
    * This field is repeatable. See
    * https://www.loc.gov/marc/bibliographic/bd020.html
    */
  private def getIsbnIdentifiers(
    bibData: SierraBibData
  ): List[SourceIdentifier] =
    bibData
      .subfieldsWithTag("020" -> "a")
      .contents
      .distinct
      .map {
        value =>
          SourceIdentifier(
            identifierType = IdentifierType.ISBN,
            ontologyType = "Work",
            value = value.trim
          )
      }

  /** Find ISSN (International Standard Serial Number) identifiers from MARC 022
    * ǂa.
    *
    * This field is repeatable. See
    * https://www.loc.gov/marc/bibliographic/bd022.html
    */
  private def getIssnIdentifiers(
    bibData: SierraBibData
  ): List[SourceIdentifier] =
    bibData
      .subfieldsWithTag("022" -> "a")
      .contents
      .distinct
      .map {
        value =>
          SourceIdentifier(
            identifierType = IdentifierType.ISSN,
            ontologyType = "Work",
            value = value.trim
          )
      }

  /** Find the digcodes from MARC 759 ǂa.
    *
    * A digcode is a Wellcome-specific identifier that identifies the
    * digitisation project under which the item was digitised. These are used by
    * staff to quickly locate, for example, all the MOH reports or everything
    * digitised from a partner institution.
    *
    * The value of the digcode should only be the contiguous alphabetic string
    * that starts with `dig`.
    *
    * Note: MARC 759 is not assigned by the MARC spec.
    */
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
          case digcodeRegex(d) =>
            d
        }

    digcodeValues.distinct
      .map {
        value =>
          SourceIdentifier(
            identifierType = IdentifierType.WellcomeDigcode,
            ontologyType = "Work",
            value = value
          )
      }
  }

  /** Add the iconographic numbers as identifiers.
    *
    * These are also included as the reference number on a Work; we add them
    * here so they're easily searchable.
    */
  private def getIconographicNumbers(
    bibData: SierraBibData
  ): List[SourceIdentifier] =
    SierraIconographicNumber(bibData).map {
      iconographicNumber =>
        SourceIdentifier(
          identifierType = IdentifierType.IconographicNumber,
          ontologyType = "Work",
          value = iconographicNumber
        )
    }.toList

  /** Add the ESTC references from MARC 510 ǂc.
    *
    * These are also included in the notes field on a Work; we add them here so
    * they're easily searchable.
    */
  private def getEstcReferences(
    bibData: SierraBibData
  ): List[SourceIdentifier] =
    bibData.varFields
      .filter {
        vf =>
          vf.marcTag.contains("510")
      }
      .map { _.subfields }
      .collect {
        // We only care about the case where there are two subfields,
        // and the contents of subfield ǂa is "ESTC".
        //
        // We ignore any other cases (e.g. different value in ǂa, repeated ǂc).
        //
        // The information will still be on the note, but it's not a reference
        // number we should make searchable.
        case Seq(Subfield("a", "ESTC"), Subfield("c", contents)) => contents
      }
      .collect {
        // Only capture values that look like ESTC citations, e.g. T102669
        //
        // This regex was created by looking at all the values in MARC 510.
        case estcRegex(identifier) => Some(identifier)
      }
      .flatten
      .map {
        value =>
          SourceIdentifier(
            identifierType = IdentifierType.ESTC,
            ontologyType = "Work",
            value = value
          )
      }

  private val estcRegex = new Regex("([TWRNPS][0-9]+)")
}
