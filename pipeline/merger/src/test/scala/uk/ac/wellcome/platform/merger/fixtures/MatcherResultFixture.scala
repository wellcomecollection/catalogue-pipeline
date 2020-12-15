package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._

trait MatcherResultFixture {
  def matcherResultWith(matchedEntries: Set[Set[Work[Identified]]]) =
    MatcherResult(
      matchedEntries.map { works =>
        MatchedIdentifiers(worksToWorkIdentifiers(works))
      }
    )

  def worksToWorkIdentifiers(
    works: Seq[Work[Identified]]): Set[WorkIdentifier] =
    worksToWorkIdentifiers(works.toSet)

  def worksToWorkIdentifiers(
    works: Set[Work[Identified]]): Set[WorkIdentifier] =
    works
      .map { work =>
        WorkIdentifier(work)
      }
}
