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
trait MarcCommonLabelSubdivisions extends ConceptsTransformer {
  protected val subdivisionSeparator = " - "
  private val subdivisionTags = Seq("v", "x", "y", "z")
  private val labelSubfieldTags = "a" +: subdivisionTags
  protected def getLabelSubfields(
    field: MarcField
  ): (Seq[MarcSubfield], Seq[MarcSubfield]) =
    field.subfields
      .filter(subfield => labelSubfieldTags.contains(subfield.tag))
      .partition { _.tag == "a" }

  // Get the label.  This is populated by the label of subfield $a, followed
  // by other subfields, in the order they come from MARC.  The labels are
  // joined by " - ".
  protected def getLabel(
    primarySubfields: Seq[MarcSubfield],
    subdivisionSubfields: Seq[MarcSubfield]
  ): String = {
    val orderedSubfields = primarySubfields ++ subdivisionSubfields
    orderedSubfields
      .map { _.content }
      .mkString(subdivisionSeparator)
      .trimTrailingPeriod
  }
  protected def getSubdivisions(
    field: MarcField
  ): Seq[AbstractConcept[IdState.Unminted]] =
    getSubdivisions(
      field.subfields.filter(subfield => subdivisionTags.contains(subfield.tag))
    )

  // Extract the subdivision as concepts of the appropriate type.
  // These are never provided with an identifier in the source data,
  // which refers either to the field as a whole, or just to the primary subfield,
  // so will always have a label-derived identifier.
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
