package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, Genre}
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.pipeline.transformer.text.TextNormalisation._
import weco.pipeline.transformer.transformers.ConceptsTransformer

trait MiroGenres extends ConceptsTransformer {
  def getGenres(miroRecord: MiroRecord): List[Genre[IdState.Unminted]] =
    // Populate the genres field.  This is based on two fields in the XML,
    // <image_phys_format> and <image_lc_genre>.
    (miroRecord.physFormat.toList ++ miroRecord.lcGenre.toList).map { label =>
      val normalisedLabel = label.sentenceCase

      val concept = Concept(label = normalisedLabel).normalised

      val genre = Genre(
        label = normalisedLabel,
        concepts = List(concept)
      )

      genre.normalised
    }.distinct
}
