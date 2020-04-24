package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}

object Sources {
  type Sources = Seq[TransformedBaseWork]

  implicit class SourcesOps(val sources: Sources) {
    def findFirstLinkedDigitisedSierraWorkFor(
      target: UnidentifiedWork): Option[TransformedBaseWork] =
      target.data.mergeCandidates
        .filter(_.reason.contains("Physical/digitised Sierra work"))
        .map(_.identifier)
        .flatMap(sourceIdentifier =>
          sources.filter(source => source.sourceIdentifier == sourceIdentifier))
        .headOption
  }
}
