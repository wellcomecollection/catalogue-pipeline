package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Concept, Place}
import weco.pipeline.transformer.text.TextNormalisation._
import weco.pipeline.transformer.transformers.{
  ConceptsTransformer,
  ParsedPeriod
}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.marc.{Subfield, VarField}

trait SierraConcepts
    extends SierraQueryOps
    with ConceptsTransformer
    with SierraAbstractConcepts {

  // Get the label.  This is populated by the label of subfield $a, followed
  // by other subfields, in the order they come from MARC.  The labels are
  // joined by " - ".
  protected def getLabel(primarySubfields: List[Subfield],
                         subdivisionSubfields: List[Subfield]): String = {
    val orderedSubfields = primarySubfields ++ subdivisionSubfields
    orderedSubfields.map { _.content }.mkString(" - ").trimTrailingPeriod
  }

  protected def getLabel(varField: VarField): Option[String] = {
    val (primarySubfields, subdivisionSubfields) = getLabelSubfields(varField)
    getLabel(primarySubfields, subdivisionSubfields) match {
      case ""    => None
      case label => Some(label)
    }
  }

  protected def getLabelSubfields(
    varField: VarField): (List[Subfield], List[Subfield]) =
    varField
      .subfieldsWithTags("a", "v", "x", "y", "z")
      .partition { _.tag == "a" }

  /** Return a list of the distinct contents of every subfield 0 on
    * this varField, which is a commonly-used subfield for identifiers.
    */
  def getIdentifierSubfieldContents(varField: VarField): List[String] =
    varField
      .subfieldsWithTag("0")
      .contents

      // We've seen the following data in subfield $0 which needs to be
      // normalisation:
      //
      //  * The same value repeated multiple times
      //    ['D000056', 'D000056']
      //
      //  * The value repeated with the prefix (DNLM)
      //    ['D049671', '(DNLM)D049671']
      //
      //    Here the prefix is denoting the authority it came from, which is
      //    an artefact of the original Sierra import.  We don't need it.
      //
      //  * The value repeated with trailing punctuation
      //    ['D004324', 'D004324.']
      //
      //  * The value repeated with varying whitespace
      //    ['n  82105476 ', 'n 82105476']
      //
      //  * The value repeated with a MESH URL prefix
      //    ['D049671', 'https://id.nlm.nih.gov/mesh/D049671']
      //
      .map { _.replaceFirst("^\\(DNLM\\)", "") }
      .map { _.replaceFirst("^https://id\\.nlm\\.nih\\.gov/mesh/", "") }
      .map { _.replaceAll("[.\\s]", "") }
      .distinct

  // Apply an identifier to the primary concept.  We look in subfield $0
  // for the identifier value, then second indicator for the authority.
  //
  def identifyConcept(ontologyType: String,
                      varField: VarField): IdState.Unminted =
    getIdState(ontologyType, varField)

  // If there's exactly one subfield $0 on the VarField, add an identifier
  // if possible.
  protected def maybeAddIdentifier(
    ontologyType: String,
    varField: VarField,
    identifierSubfieldContent: String): IdState.Unminted =
    SierraConceptIdentifier
      .maybeFindIdentifier(
        varField = varField,
        identifierSubfieldContent = identifierSubfieldContent,
        ontologyType = ontologyType
      )
      .map(IdState.Identifiable(_))
      .getOrElse(IdState.Unidentifiable)

  // Extract the subdivisions, which come from everything except subfield $a.
  // These are never identified.  We preserve the order from MARC.
  protected def getSubdivisions(subdivisionSubfields: List[Subfield])
    : List[AbstractConcept[IdState.Unminted]] =
    subdivisionSubfields.map { subfield =>
      subfield.tag match {
        case "v" | "x" =>
          Concept(label = subfield.content).normalised.identifiable()
        case "y" => ParsedPeriod(label = subfield.content).identifiable()
        case "z" => Place(label = subfield.content).normalised.identifiable()
      }
    }
}
