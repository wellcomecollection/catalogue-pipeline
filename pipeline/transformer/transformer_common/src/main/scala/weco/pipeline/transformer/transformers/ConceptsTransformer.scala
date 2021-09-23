package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.work.Genre

trait ConceptsTransformer {
  implicit class GenreOps[State](g: Genre[State]) {
    def normalised: Genre[State] = {
      val normalisedLabel =
        g.label
          .stripSuffix(".")
          .trim
          .replace("Electronic Books", "Electronic books")

      g.copy(label = normalisedLabel)
    }
  }
}
