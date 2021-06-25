package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, Genre}
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroGenres {
  def getGenres(miroRecord: MiroRecord): List[Genre[IdState.Unminted]] =
    // Populate the genres field.  This is based on two fields in the XML,
    // <image_phys_format> and <image_lc_genre>.
    (miroRecord.physFormat.toList ++ miroRecord.lcGenre.toList).map { label =>
      val normalisedLabel = label.sentenceCase
      Genre.normalised(
        label = normalisedLabel,
        concepts = List(Concept.normalised(label = normalisedLabel))
      )
    }.distinct
}
