package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.marc_common.models.MarcField

// Implements logic for finding a source identifier for varFields with
// MARC tag 648, 650, 651 and 655.  These are the fields we use for genre
// and subject.
//
// The rules for these identifiers is moderately fiddly:
//
//   - Look in indicator 2.  Values 0 to 6 have hard-coded meanings.
//   - If indicator 2 is 7, look in subfield $2 instead.
//
// The rules are the same for all four MARC tags, hence the shared object.
//
// https://www.loc.gov/marc/bibliographic/bd648.html
// https://www.loc.gov/marc/bibliographic/bd650.html
// https://www.loc.gov/marc/bibliographic/bd651.html
// https://www.loc.gov/marc/bibliographic/bd655.html

trait MarcHasRecordControlNumber extends LabelDerivedIdentifiers with Logging {
  protected val defaultSecondIndicator: String = ""
  private val urlLocPrefix: String = "http://idlocgov/authorities/subjects/"

  protected def getLabel(field: MarcField): Option[String] =
    Option(field.subfields.filter(_.tag == "a").map(_.content).mkString(" "))
      .filter(_.isEmpty)

  protected def normalise(identifier: String): String = {
    // Sort out dodgy punctuation and spacing and remove a URL prefix which exists on some otherwise valid LCSH IDs
    identifier
      .replaceAll("[,.\\s]", "")
      .stripPrefix(urlLocPrefix)
  }

  private def getIdentifierSubfieldContents(field: MarcField): Seq[String] =
    field.subfields
      .filter(_.tag == "0")
      .map(subfield => normalise(subfield.content))
      .distinct

  /** Determine the Library of Congress identifier type from the identifier
    * value prefix.
    *
    * There are multiple LoC schemes that may be indicated by the same value in
    * indicator2, The two we are interested in are LCSubjects and LCNames. These
    * can be differentiated by the first character in the identifier.
    *
    * Technically, the LoC schemes are differentiated by a sequence of
    * alphabetic characters before the numbers start.
    *   - LC Names identifiers all have a one or two character prefix starting
    *     with n - e.g. n, no, nb.
    *   - LC Subjects identifiers all start with sh.
    *
    * There are other schemes that start with s (e.g. sj: Children's Subject
    * Headings), but they are not in use in Wellcome data.
    *
    * In concise definition of these fields, (e.g.
    * https://www.loc.gov/marc/bibliographic/concise/bd648.html) * a second
    * indicator value of 0 indicates that the identifier comes from LCSH: 0 -
    * Library of Congress Subject Headings However, the extended description
    * (e.g. https://www.loc.gov/marc/bibliographic/bd648.html) goes on to also
    * include the "Name authority files" (i.e. LCNames). 0 - Library of Congress
    * Subject Headings Subject added entry conforms to and is appropriate for
    * use in the Library of Congress Subject Headings (LCSH) and the Name
    * authority files that are maintained by the Library of Congress.
    */
  private def locScheme(idValue: String): IdentifierType = {
    idValue.split("\\d", 2).head match {
      // sh is the only legal prefix for a Subject Headings identifier.
      // At time of writing, there were some other s~ prefixed identifiers in use and marked as
      // LCSH in the Works Catalogue, but these appear to be errors, so an exact match is
      // enforced here in order to guard against such typos re-emerging.
      case "sh" => IdentifierType.LCSubjects
      // There are actually seven different prefixes in use in the whole LCNames Authority file,
      // but because there is no definitive list of LCNames prefixes, we do not wish to accidentally
      // exclude a real one in the future by insisting that the prefix we find must be a member of
      // a closed list.
      case prefix if prefix.headOption.contains('n') => IdentifierType.LCNames
      // Any prefix other than sh or n is an error. Common mistakes include MeSH ids marked as LoC ids
      // and general typographical errors such as `shsh`.  Guard against these by rejecting the identifier.
      case _ =>
        throw new IllegalArgumentException(
          s"Could not determine LoC scheme from id '$idValue'"
        )

    }
  }

  def getIdState(
    field: MarcField,
    ontologyType: String
  ): IdState.Unminted = {
    val indicator2 = getSecondIndicator(field)

    getIdentifierSubfieldContents(field) match {
      case Seq(subfieldContent) =>
        getSourceIdentifier(
          indicator2 = indicator2,
          identifierValue = subfieldContent,
          ontologyType = ontologyType
        ).map(IdState.Identifiable(_))
          .getOrElse(IdState.Unidentifiable)
      case Nil =>
        getLabelDerivedIdentifier(ontologyType, field)
      case values =>
        // TODO: throw here?
        warn(
          s"unable to identify definitively, multiple identifier subfields found on $field"
        )
        handleMultipleIdFields(
          values,
          getLabelDerivedIdentifier(ontologyType, field)
        )

    }
  }
  protected def handleMultipleIdFields(
    values: Seq[String],
    candidateIdentifier: IdState.Unminted
  ): IdState.Unminted = candidateIdentifier

  protected def getSecondIndicator(field: MarcField): String =
    if (field.indicator2.trim().isEmpty) defaultSecondIndicator
    else field.indicator2

  protected def getIdentifierType(
    indicator2: String,
    identifierValue: String
  ): Option[IdentifierType] =
    indicator2 match {
      // These mappings are provided by the MARC spec.
      // https://www.loc.gov/marc/bibliographic/bd655.html
      case "0" => Some(locScheme(identifierValue))
      case "2" => Some(IdentifierType.MESH)
      // For now we omit the other schemes as they're fairly unusual in
      // our collections.  If ind2 = "7", then we need to look in another
      // subfield to find the identifier scheme.  For now, we just highlight
      // LCSH and MESH, and drop everything else.
      case _ => None
    }
  private def getSourceIdentifier(
    indicator2: String,
    identifierValue: String,
    ontologyType: String
  ): Option[SourceIdentifier] = {

    getIdentifierType(indicator2, identifierValue) match {
      case None => None
      case Some(identifierType) =>
        Some(
          SourceIdentifier(
            identifierType = identifierType,
            value = identifierValue,
            ontologyType = ontologyType
          )
        )
    }
  }

  protected def getLabelDerivedIdentifier(
    ontologyType: String,
    field: MarcField
  ): IdState.Unminted =
    getLabel(field) match {
      case Some(label) =>
        identifierFromText(label = label, ontologyType = ontologyType)
      case None => IdState.Unidentifiable
    }
}

object MarcHasRecordControlNumber extends MarcHasRecordControlNumber {
  override protected def getLabel(field: MarcField): Option[String] = None

  def apply(field: MarcField, ontologyType: String): IdState.Unminted =
    getIdState(field, ontologyType)
}
