package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Genre
import weco.pipeline.transformer.marc_common.models.MarcField

object MarcGenre
    extends MarcFieldTransformer
    with SubdividedLabel
    with Logging {
  override type Output = Option[Genre[IdState.Unminted]]

  def apply(field: MarcField): Output = {
    val (primarySubfields, subdivisionSubfields) =
      getLabelSubfields(field)

    val label = getLabel(primarySubfields, subdivisionSubfields)
    val concepts = getPrimaryConcept(
      primarySubfields,
      varField = varField
    ) ++ getSubdivisions(subdivisionSubfields)
    label match {
      case "" => None
      case nonEmptyLabel =>
        Some(Genre(label = nonEmptyLabel, concepts = concepts).normalised)
    }
  }
}
