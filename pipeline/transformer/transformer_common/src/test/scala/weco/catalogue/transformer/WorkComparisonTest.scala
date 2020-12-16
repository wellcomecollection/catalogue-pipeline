package weco.catalogue.transformer

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators

class WorkComparisonTest extends AnyFunSpec with Matchers with WorkGenerators {
  import WorkComparison._

  describe("SourceWork.shouldReplace") {
    it("a work should not replace itself") {
      val work = sourceWork()

      work.shouldReplace(work) shouldBe false
    }

    it("if only the version has changed, then a work should not be replaced") {
      val olderWork = sourceWork()
      val newerWork = olderWork.withVersion(olderWork.version + 1)

      olderWork.shouldReplace(newerWork) shouldBe false
      newerWork.shouldReplace(olderWork) shouldBe false
    }

    it("if the proposed work is older than the stored work, then the stored work should not be replaced") {
      val work = sourceWork()
      val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version + 1)

      work.shouldReplace(storedWork) shouldBe false
      storedWork.shouldReplace(work) shouldBe true
    }

    it("if a proposed work has the same version and different data, then it should replace the stored work") {
      val work = sourceWork()
      val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version)

      work.shouldReplace(storedWork) shouldBe true
    }

    it("if a proposed work has a higher version and different data, then it should replace the stored work") {
      val work = sourceWork()
      val storedWork = sourceWork(sourceIdentifier = work.sourceIdentifier).withVersion(work.version - 1)

      work.shouldReplace(storedWork) shouldBe true
    }
  }
}
