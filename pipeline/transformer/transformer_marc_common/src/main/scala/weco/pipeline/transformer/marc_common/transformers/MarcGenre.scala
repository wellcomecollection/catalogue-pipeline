package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Genre, GenreConcept}
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

object MarcGenre
    extends MarcFieldTransformer
    with MarcCommonLabelSubdivisions
    with MarcHasRecordControlNumber
    with Logging {
  override type Output = Option[Genre[IdState.Unminted]]

  override def getLabelDerivedIdentifier(
    ontologyType: String,
    field: MarcField
  ): IdState.Unminted = IdState.Unidentifiable
  def apply(field: MarcField): Output = {
    val (primarySubfields, subdivisionSubfields) =
      getLabelSubfields(field)

    val label = getLabel(primarySubfields, subdivisionSubfields)
    val concepts = getPrimaryConcept(
      primarySubfields,
      field = field
    ) ++ getSubdivisions(subdivisionSubfields)
    label match {
      case "" => None
      case nonEmptyLabel =>
        Some(
          Genre(label = nonEmptyLabel, concepts = concepts.toList).normalised
        )
    }
  }

  private def getPrimaryConcept(
    primarySubfields: Seq[MarcSubfield],
    field: MarcField
  ): Seq[AbstractConcept[IdState.Unminted]] =
    primarySubfields.map {
      subfield =>
        val identifier = getIdState(field, "Genre")
        GenreConcept(
          id = identifier,
          label = subfield.content
        ).normalised.identifiable()
    }
}
