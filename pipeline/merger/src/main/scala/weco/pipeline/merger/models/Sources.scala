package weco.pipeline.merger.models

import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.rules.WorkPredicates

object Sources {
  import WorkPredicates._

  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]): Option[Work[Identified]] =
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

      // We've seen Sierra bib/e-bib pairs that go in both directions, i.e.
      //
      //      bib { 776 $w -> e-bib}
      //
      // and
      //
      //      e-bib { 776 $w -> bib }
      //
      // We need to handle both cases.
      //
      case t if physicalSierra(t) && t.state.mergeCandidates.nonEmpty =>
        val digitisedLinkedIds = target.state.mergeCandidates
          .filter(_.reason.contains("Physical/digitised Sierra work"))
          .map(_.id.canonicalId)

        sources.find(source =>
          digitisedLinkedIds.contains(source.state.canonicalId))

      case t if physicalSierra(t) =>
        sources
          .filter { w =>
            sierraWork(w) && allDigitalLocations(w)
          }
          .find { w =>
            w.state.mergeCandidates.exists { mc =>
              mc.reason == "Physical/digitised Sierra work" &&
              mc.id.canonicalId == target.state.canonicalId
            }
          }

      case _ => None
    }
}
