package weco.pipeline.merger.fixtures

import weco.fixtures.RandomGenerators
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}

import java.time.Instant

trait MatcherResultFixture extends RandomGenerators {
  def createMatcherResultWith(
    matchedEntries: Set[Set[Work[Identified]]],
    createdTime: Instant = randomInstant): MatcherResult =
    MatcherResult(
      works = matchedEntries.map { works =>
        MatchedIdentifiers(works.map { WorkIdentifier(_) })
      },
      createdTime = createdTime
    )
}
