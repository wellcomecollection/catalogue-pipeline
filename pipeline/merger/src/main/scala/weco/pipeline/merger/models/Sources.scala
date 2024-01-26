package weco.pipeline.merger.models

import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.rules.WorkPredicates

object Sources {
  import WorkPredicates._

  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Option[Work[Identified]] =
    target match {
      // Audiovisual works are catalogued as multiple bib records, one for the physical
      // format and another for the digitised version, where available.
      //
      // These bibs routinely contain different data, and we can't consider one of
      // them canonical, unlike other physical/digitised bib pairs.
      //
      // Longer term, this may change based if Collections Information change how
      // they catalogue AV works.
      //
      // See https://github.com/wellcomecollection/platform/issues/4876
      case t if isAudiovisual(t) =>
        None

      // Handle the case where the physical bib links to the e-bib.
      //
      // Note: the physical bib may have merge candidates to other works, so
      // we need to make sure we don't match this branch if that's the case.
      case t if physicalSierra(t) && hasPhysicalDigitalMergeCandidate(t) =>
        val digitisedLinkedIds = target.state.mergeCandidates
          .filter(_.reason.contains("Physical/digitised Sierra work"))
          .map(_.id.canonicalId)

        sources.find(source => digitisedLinkedIds.contains(source.state.canonicalId))

      // Handle the case where the e-bib links to the physical bib, but not
      // the other way round.
      case t if physicalSierra(t) =>
        sources
          .filter {
            w =>
              sierraWork(w) && allDigitalLocations(w)
          }
          .find {
            w =>
              w.state.mergeCandidates.exists {
                mc =>
                  mc.reason == "Physical/digitised Sierra work" &&
                  mc.id.canonicalId == target.state.canonicalId
              }
          }

      case _ => None
    }
}
