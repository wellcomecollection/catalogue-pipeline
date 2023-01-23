package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.sierra.models.marc.VarField

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
//
object SierraConceptIdentifier extends Logging {

  /**
    * Determine the Library of Congress identifier type from the identifier value prefix.
    *
    * There are multiple LoC schemes that may be indicated by the same value in indicator2,
    * The two we are interested in are LCSubjects and LCNames.  These can be differentiated by
    * the first character in the identifier.
    *
    * Technically, the LoC schemes are differentiated by a sequence of alphabetic characters before
    * the numbers start.
    * - LC Names identifiers all have a one or two character prefix starting with n - e.g. n, no, nb.
    * - LC Subjects identifiers all start with sh.
    *
    * There are other schemes that start with s (e.g. sj: Children's Subject Headings), but they are not
    * in use in Wellcome data.
    *
    * In concise definition of these fields, (e.g. https://www.loc.gov/marc/bibliographic/concise/bd648.html)
    * * a second indicator value of 0 indicates that the identifier comes from LCSH:
    * 0 - Library of Congress Subject Headings
    * However, the extended description (e.g. https://www.loc.gov/marc/bibliographic/bd648.html) goes on
    * to also include the "Name authority files" (i.e. LCNames).
    * 0 - Library of Congress Subject Headings
    *  Subject added entry conforms to and is appropriate for use in the Library of Congress Subject Headings (LCSH) and the Name authority files that are maintained by the Library of Congress.
    *
    */
  private def locScheme(idValue: String): IdentifierType =
    idValue.split("\\d", 2).head match {
      // sh is the only legal prefix for a Subject Headings identifier.
      // At time of writing, there were some other s~ prefixed identifiers in use and marked as
      // LCSH in the Works Catalogue, but these appear to be errors, so an exact match is
      // enforced here in order to guard against such typos re-emerging.
      case "sh" => IdentifierType.LCSubjects
      // MESH is not a Library of Congress scheme, but a common error found in source documents
      // is to mark a MeSH id as a LoC id. Fix it, but also log a warning
      case "D" =>
        warn(s"MeSH identifier found masquerading as LoC identifier: $idValue")
        IdentifierType.MESH
      // There are actually seven different prefixes in use in the whole LCNames Authority file,
      // but because there is no definitive list of LCNames prefixes, we do not wish to accidentally
      // exclude a real one in the future by insisting that the prefix we find must be a member of
      // a closed list.
      case prefix if prefix.head == 'n' => IdentifierType.LCNames
      // Any prefix other than sh or n is an error. Common mistakes include MeSH ids marked as LoC ids
      // and general typographical errors such as `shsh`.  Guard against these by rejecting the identifier.
      case _ =>
        throw new IllegalArgumentException(
          s"Could not determine LoC scheme from id '$idValue'"
        )

    }
  def maybeFindIdentifier(
    varField: VarField,
    identifierSubfieldContent: String,
    ontologyType: String
  ): Option[SourceIdentifier] = {
    val maybeIdentifierType = varField.indicator2 match {
      case None => None

      // These mappings are provided by the MARC spec.
      // https://www.loc.gov/marc/bibliographic/bd655.html
      case Some("0") => Some(locScheme(identifierSubfieldContent))
      case Some("2") => Some(IdentifierType.MESH)
      case Some("4") => None

      // For now we omit the other schemes as they're fairly unusual in
      // our collections.  If ind2 = "7", then we need to look in another
      // subfield to find the identifier scheme.  For now, we just highlight
      // LCSH and MESH, and drop everything else.
      // TODO: Revisit this properly.
      case Some(scheme) => None
    }

    maybeIdentifierType match {
      case None => None
      case Some(identifierType) =>
        Some(
          SourceIdentifier(
            identifierType = identifierType,
            value = identifierSubfieldContent,
            ontologyType = ontologyType
          )
        )
    }
  }
}
