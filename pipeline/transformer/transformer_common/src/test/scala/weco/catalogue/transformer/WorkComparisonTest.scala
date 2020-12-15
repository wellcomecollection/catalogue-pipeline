package weco.catalogue.transformer

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators

class WorkComparisonTest extends AnyFunSpec with Matchers with WorkGenerators {
  it("a work is not newer than itself") {
    val work = sourceWork()

    WorkComparison.isNewer(work, work) shouldBe false
  }

  it("if a work is the same up to version, then it is not newer") {
    val olderWork = sourceWork()
    val newerWork = olderWork.withVersion(olderWork.version + 1)

    WorkComparison.isNewer(olderWork, newerWork) shouldBe false
    WorkComparison.isNewer(newerWork, olderWork) shouldBe false
  }

  it("a work is never newer than a work with a higher version") {
    val work = sourceWork()
    val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version + 1)

    WorkComparison.isNewer(storedWork, work) shouldBe false
  }

  it("a work is newer than a work with the same version if it has different data") {
    val work = sourceWork()
    val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version)

    WorkComparison.isNewer(storedWork, work) shouldBe true
  }

  it("a work is newer than a work with a lower version if it has different data") {
    val work = sourceWork()
    val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version - 1)

    WorkComparison.isNewer(storedWork, work) shouldBe true
  }
}
