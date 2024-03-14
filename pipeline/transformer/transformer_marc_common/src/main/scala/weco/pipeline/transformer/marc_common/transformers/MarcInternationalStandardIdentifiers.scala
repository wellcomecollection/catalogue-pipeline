package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.marc_common.models.MarcRecord

trait MarcInternationalStandardIdentifiers {

  private def getInternationalStandardNumber(
    record: MarcRecord,
    identifierType: IdentifierType
  ): Seq[SourceIdentifier] = {
    val marcTag = identifierType match {
      case IdentifierType.ISBN => "020"
      case IdentifierType.ISSN => "022"
      case _                   => throw new Exception()
    }
    record
      .subfieldsWithTag(marcTag -> "a")
      .map(_.content)
      .distinct
      .map {
        value =>
          SourceIdentifier(
            identifierType = identifierType,
            ontologyType = "Work",
            value = value.trim
          )
      }
  }

  /** Find ISBN (International Standard Book Number) identifiers from MARC 020
    * ǂa.
    *
    * This field is repeatable. See
    * https://www.loc.gov/marc/bibliographic/bd020.html
    */
  protected def getIsbnIdentifiers(
    record: MarcRecord
  ): Seq[SourceIdentifier] =
    getInternationalStandardNumber(record, IdentifierType.ISBN)

  /** Find ISSN (International Standard Serial Number) identifiers from MARC 022
    * ǂa.
    *
    * This field is repeatable. See
    * https://www.loc.gov/marc/bibliographic/bd022.html
    */
  protected def getIssnIdentifiers(
    record: MarcRecord
  ): Seq[SourceIdentifier] =
    getInternationalStandardNumber(record, IdentifierType.ISSN)

}

object MarcInternationalStandardIdentifiers
    extends MarcInternationalStandardIdentifiers {
  def apply(record: MarcRecord): Seq[SourceIdentifier] =
    (getIsbnIdentifiers(record) ++ getIssnIdentifiers(record)).distinct
}
