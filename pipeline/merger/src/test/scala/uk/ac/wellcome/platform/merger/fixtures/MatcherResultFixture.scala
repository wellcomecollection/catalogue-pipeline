package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.models.work.internal._
import WorkState.Unidentified

trait MatcherResultFixture {
  def matcherResultWith(matchedEntries: Set[Set[Work[Unidentified]]]) =
    MatcherResult(
      matchedEntries.map { works =>
        MatchedIdentifiers(worksToWorkIdentifiers(works))
      }
    )

  def worksToWorkIdentifiers(
    works: Seq[Work[Unidentified]]): Set[WorkIdentifier] =
    worksToWorkIdentifiers(works.toSet)

  def worksToWorkIdentifiers(
    works: Set[Work[Unidentified]]): Set[WorkIdentifier] =
    works
      .map { work =>
        WorkIdentifier(work)
      }
}
