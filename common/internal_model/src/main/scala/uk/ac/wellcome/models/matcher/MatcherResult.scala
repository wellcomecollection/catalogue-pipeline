package uk.ac.wellcome.models.matcher

import java.time.Instant

// Represents the output from the matcher.
//
// Each entry in the set of works is a collection of identifiers, each of
// which should be merged into a single work.
//
// For example, if we had the result:
//
//    MatchResult([
//      {A1, A2, A3},
//      {B1, B2},
//      {C1, C2, C3}
//    ])
//
// then the merger should create three works, one from A1-A2-A3, a second
// from B1-B2, a third from C1-C2-C3.
//
case class MatcherResult(
  works: Set[MatchedIdentifiers],
  createdTime: Instant
)

case object MatcherResult {
  // Note: I could achieve this by putting a default in the case class, but
  // doing it this way means I can "Find Usages" on this method to find callers
  // that *don't* explicitly set the correct time.
  //
  // This method will only last as long as https://github.com/wellcomecollection/platform/issues/5132,
  // after which it can be deleted -- all uses should be setting the time explicitly.
  def apply(works: Set[MatchedIdentifiers]): MatcherResult =
    MatcherResult(works = works, createdTime = Instant.now())
}
