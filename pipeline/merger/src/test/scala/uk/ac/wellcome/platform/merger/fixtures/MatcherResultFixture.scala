package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work

trait MatcherResultFixture extends RandomGenerators {
  def createMatcherResultWith(matchedEntries: Set[Set[Work[Identified]]]) =
    MatcherResult(
      works = matchedEntries.map { works =>
        MatchedIdentifiers(worksToWorkIdentifiers(works))
      },
      createdTime = randomInstant
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
