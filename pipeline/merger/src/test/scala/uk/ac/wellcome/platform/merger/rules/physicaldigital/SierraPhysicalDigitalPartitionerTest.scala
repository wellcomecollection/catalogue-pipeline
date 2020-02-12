package uk.ac.wellcome.platform.merger.rules.physicaldigital

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.merger.model.PotentialMergedWork
import uk.ac.wellcome.platform.merger.rules.Partition

class SierraPhysicalDigitalPartitionerTest
    extends FunSpec
    with WorksGenerators
    with Matchers {

  private val partitioner = new SierraPhysicalDigitalPartitioner {}
  private val physicalWork = createSierraPhysicalWork
  private val digitalWork = createSierraDigitalWork
  private val otherWorks = createIsbnWorks(4)

  it("partitions a physical and digital work") {
    val result = partitioner.partitionWorks(Seq(physicalWork, digitalWork))

    result shouldBe Some(
      Partition(PotentialMergedWork(physicalWork, digitalWork), Nil))
  }

  it("partitions physical work with multiple physical items and digital work") {
    val sierraWorkWithTwoPhysicalItems = createSierraWorkWithTwoPhysicalItems
    val result = partitioner.partitionWorks(
      Seq(sierraWorkWithTwoPhysicalItems, digitalWork))

    result shouldBe Some(
      Partition(
        PotentialMergedWork(sierraWorkWithTwoPhysicalItems, digitalWork),
        Nil))
  }

  it(
    "partitions physical work with a mixture of physical and digital items and digital work") {
    val sierraWorkWithTwoItems = createUnidentifiedSierraWorkWith(
      items = List(createPhysicalItem, createDigitalItem)
    )
    val result =
      partitioner.partitionWorks(Seq(sierraWorkWithTwoItems, digitalWork))

    result shouldBe Some(
      Partition(PotentialMergedWork(sierraWorkWithTwoItems, digitalWork), Nil))
  }

  it("partitions a physical and digital work, order in sequence") {
    val result = partitioner.partitionWorks(Seq(digitalWork, physicalWork))

    result shouldBe Some(
      Partition(PotentialMergedWork(physicalWork, digitalWork), Nil))
  }

  it("partitions a physical, digital and other works") {
    val result =
      partitioner.partitionWorks(Seq(physicalWork, digitalWork) ++ otherWorks)

    result shouldBe Some(
      Partition(PotentialMergedWork(physicalWork, digitalWork), otherWorks))
  }

  it("does not partition a digital work and a non physical sierra work") {
    val result =
      partitioner.partitionWorks(Seq(digitalWork, createUnidentifiedSierraWork))

    result shouldBe None
  }

  it("does not partition a single physical work") {
    val result = partitioner.partitionWorks(Seq(physicalWork))

    result shouldBe None
  }

  it("does not partition a single digital work") {
    val result = partitioner.partitionWorks(Seq(digitalWork))

    result shouldBe None
  }

  it("does not partition multiple physical works") {
    val works = (1 to 3).map { _ =>
      createSierraPhysicalWork
    }

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple digital works") {
    val works = (1 to 3).map { _ =>
      createSierraDigitalWork
    }
    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple non digital or physical works") {
    val result = partitioner.partitionWorks(otherWorks)

    result shouldBe None
  }

  it("does not partition multiple physical works with a single digital work") {
    val works = (1 to 3).map { _ =>
      createSierraPhysicalWork
    } ++ Seq(createSierraDigitalWork)

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }

  it("does not partition multiple digital works with a single physical work") {
    val works = (1 to 3).map { _ =>
      createSierraDigitalWork
    } ++ Seq(createSierraPhysicalWork)

    val result = partitioner.partitionWorks(works)

    result shouldBe None
  }
}
