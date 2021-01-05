package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.internal.IdState.Identifiable

/** Indicates that it might be possible to merge this Work with another Work.
  *
  * @param id The SourceIdentifier of the other Work.
  * @param reason A statement of _why_ the we think it might be possible to
  *               to merge these two works.  For example, "MARC tag 776 points
  *               to electronic resource".
  *
  *               Long-term, this might be replaced with an enum or a fixed
  *               set of strings.
  */
case class MergeCandidate[+State](
  id: State,
  reason: Option[String] = None
) extends HasId[State]

case object MergeCandidate {
  def apply(identifier: SourceIdentifier,
            reason: String): MergeCandidate[Identifiable] =
    MergeCandidate(
      id = Identifiable(sourceIdentifier = identifier),
      reason = Some(reason)
    )
}
