package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.work.{Genre, Meeting, Person}

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

  implicit class MeetingOps[State](m: Meeting[State]) {
    def normalised: Meeting[State] =
      m.copy(label = m.label.trimTrailing(','))
  }

  implicit class PersonOps[State](p: Person[State]) {
    def normalised: Person[State] =
      p.copy(label = p.label.trimTrailing(','))
  }
}
