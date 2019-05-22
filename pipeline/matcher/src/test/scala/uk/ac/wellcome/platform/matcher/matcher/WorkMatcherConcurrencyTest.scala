package uk.ac.wellcome.platform.matcher.matcher

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.MergeCandidate
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures

class WorkMatcherConcurrencyTest
    extends FunSpec
    with Matchers
    with MatcherFixtures
    with WorksGenerators {

  it("processes one of two conflicting concurrent updates and locks the other") {
    withWorkGraphTable { graphTable =>
      val lockDao = createLockDao

      val workMatcher = createWorkMatcher(
        graphTable = graphTable,
        lockingService = createLockingService(lockDao)
      )

      val identifierA = createSierraSystemSourceIdentifierWith(value = "A")
      val identifierB = createSierraSystemSourceIdentifierWith(value = "B")

      val workA = createUnidentifiedWorkWith(
        sourceIdentifier = identifierA,
        mergeCandidates = List(MergeCandidate(identifierB))
      )

      val workB = createUnidentifiedWorkWith(
        sourceIdentifier = identifierB
      )

      val results = Seq(
        workMatcher.matchWork(workA),
        workMatcher.matchWork(workB)
      )

      results.count { _.isFailure } shouldBe 1
      results.count { _.isSuccess } shouldBe 1

      lockDao.getCurrentLocks shouldBe empty
      lockDao.history should have size 1
    }
}
