package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.{Language, LanguageNote}

object CalmLanguages {
  def apply(languagesField: Option[String]): (Seq[Language], Option[LanguageNote]) =
    (Seq.empty, None)
}
