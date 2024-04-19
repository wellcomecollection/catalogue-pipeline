package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Concept, Place}
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
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
    with SierraAbstractConcepts
    with SierraMarcDataConversions {
  // Get the label.  This is populated by the label of subfield $a, followed
  // by other subfields, in the order they come from MARC.  The labels are
  // joined by " - ".
  protected def getLabel(
    primarySubfields: List[Subfield],
    subdivisionSubfields: List[Subfield]
  ): String = {
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
    varField: VarField
  ): (List[Subfield], List[Subfield]) =
    varField
      .subfieldsWithTags("a", "v", "x", "y", "z")
      .partition { _.tag == "a" }

  // Extract the subdivisions, which come from everything except subfield $a.
  // These are never identified.  We preserve the order from MARC.
  protected def getSubdivisions(
    subdivisionSubfields: List[Subfield]
  ): List[AbstractConcept[IdState.Unminted]] =
    subdivisionSubfields.map {
      subfield =>
        subfield.tag match {
          case "v" | "x" =>
            Concept(label = subfield.content).normalised.identifiable()
          case "y" => ParsedPeriod(label = subfield.content).identifiable()
          case "z" => Place(label = subfield.content).normalised.identifiable()
        }
    }
}
