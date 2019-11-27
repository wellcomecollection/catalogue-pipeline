package uk.ac.wellcome.platform.merger.rules.sierramets

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.merger.rules.Partition

class SierraMetsPartitionerTest
    extends FunSpec
    with WorksGenerators
    with Matchers {
  private val partitioner = new SierraMetsPartitioner {}
  private val sierraWork = createSierraPhysicalWork
  private val metsWork = createMetsInvisibleWork
  private val otherWorks = createIsbnWorks(4)

  it("partitions a sierra physical and a mets work") {
    val result = partitioner.partitionWorks(Seq(sierraWork, metsWork))

    result shouldBe Some(Partition(sierraWork, metsWork, Nil))
  }

  it("partitions a Sierra and mets work, order in sequence") {
    val result = partitioner.partitionWorks(Seq(metsWork, sierraWork))

    result shouldBe Some(Partition(sierraWork, metsWork, Nil))
  }

  it("partitions a Sierra, Mets and other works") {
    val result =
      partitioner.partitionWorks(Seq(sierraWork, metsWork) ++ otherWorks)

    result shouldBe Some(Partition(sierraWork, metsWork, otherWorks))
  }

  it("does not partition a single Sierra work") {
    val result = partitioner.partitionWorks(Seq(sierraWork))

    result shouldBe None
  }

  it("does not partition a single Mets work") {
    val result = partitioner.partitionWorks(Seq(metsWork))

    result shouldBe None
  }

  it("does not partition multiple Sierra works") {
    val works = (1 to 3).map { _ =>
      createSierraPhysicalWork
    }

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple Mets works") {
    val works = (1 to 3).map { _ =>
      createMetsInvisibleWork
    }
    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple non Mets or Sierra works") {
    val result = partitioner.partitionWorks(otherWorks)

    result shouldBe None
  }

  it("does not partition multiple Sierra works with a single Mets work") {
    val works = (1 to 3).map { _ =>
      createSierraPhysicalWork
    } ++ Seq(createMetsInvisibleWork)

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple Mets works with a single Sierra work") {
    val works = (1 to 3).map { _ =>
      createMetsInvisibleWork
    } ++ Seq(createSierraPhysicalWork)

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }
}
