package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Concept, Place}
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}
import weco.pipeline.transformer.text.TextNormalisation._
import weco.pipeline.transformer.transformers.{
  ConceptsTransformer,
  ParsedPeriod
}

/*
 * trait for dealing with those 6xx fields that contain a
 * subdivision section alongside their main label $a
 * $v - Form subdivision (R)
 * $x - General subdivision (R)
 * $y - Chronological subdivision (R)
 * $z - Geographic subdivision (R)
 * */
trait MarcHasSubdividedLabel extends ConceptsTransformer {

  protected def getLabelSubfields(
    field: MarcField
  ): (Seq[MarcSubfield], Seq[MarcSubfield]) =
    field.subfields
      .filter(subfield => Seq("a", "v", "x", "y", "z").contains(subfield.tag))
      .partition { _.tag == "a" }

  // Get the label.  This is populated by the label of subfield $a, followed
  // by other subfields, in the order they come from MARC.  The labels are
  // joined by " - ".
  protected def getLabel(
    primarySubfields: Seq[MarcSubfield],
    subdivisionSubfields: Seq[MarcSubfield]
  ): String = {
    val orderedSubfields = primarySubfields ++ subdivisionSubfields
    orderedSubfields.map { _.content }.mkString(" - ").trimTrailingPeriod
  }

  // Extract the subdivisions, which come from everything except subfield $a.
  // These are never identified.  We preserve the order from MARC.
  protected def getSubdivisions(
    subdivisionSubfields: Seq[MarcSubfield]
  ): Seq[AbstractConcept[IdState.Unminted]] =
    subdivisionSubfields.map {
      subfield =>
        subfield.tag match {
          case "v" | "x" =>
            Concept(label = subfield.content.trimTrailingPeriod).identifiable()
          case "y" => ParsedPeriod(label = subfield.content).identifiable()
          case "z" =>
            Place(label = subfield.content.trimTrailingPeriod).identifiable()
        }
    }
}
