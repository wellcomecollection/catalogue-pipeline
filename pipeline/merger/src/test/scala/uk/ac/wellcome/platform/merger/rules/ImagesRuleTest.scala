package uk.ac.wellcome.platform.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, OptionValues, PrivateMethodTester}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators
import uk.ac.wellcome.platform.merger.rules.ImagesRule.FlatImageMergeRule
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
import WorkState.Source
import uk.ac.wellcome.models.work.generators.SierraWorkGenerators

class ImagesRuleTest
    extends AnyFunSpec
    with Matchers
    with WorksWithImagesGenerators
    with SierraWorkGenerators
    with PrivateMethodTester
    with OptionValues
    with Inspectors {
  describe("image creation rules") {
    it("creates n images from n Miro works and a single Sierra work") {
      val n = 3
      val miroWorks = (1 to n).map(_ => createMiroWork)
      val sierraWork = sierraDigitalSourceWork()
      val result = ImagesRule.merge(sierraWork, miroWorks.toList).data

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        miroWorks.map(_.data.images.head.location)
    }

    it(
      "creates n images from a METS work containing n images, and a single Sierra picture work") {
      val n = 5
      val metsWork = createInvisibleMetsSourceWorkWith(numImages = n)
      val sierraPictureWork = sierraSourceWork().format(Format.Pictures)
      val result = ImagesRule.merge(sierraPictureWork, List(metsWork)).data

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location)
    }

    it(
      "creates n + m images from m Miro works, a METS work containing n images, and a single Sierra picture work") {
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => createMiroWork).toList
      val metsWork = createInvisibleMetsSourceWorkWith(numImages = n)
      val sierraPictureWork = sierraSourceWork().format(Format.Pictures)
      val result =
        ImagesRule.merge(sierraPictureWork, miroWorks :+ metsWork).data

      result should have length n + m
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location) ++
          miroWorks.map(_.data.images.head.location)
    }

    it(
      "ignores METS images, but uses n Miro images, for a non-picture Sierra work") {
      val n = 3
      val metsWork = createInvisibleMetsSourceWorkWith(numImages = 3)
      val miroWorks = (1 to n).map(_ => createMiroWork).toList
      val sierraWork = sierraDigitalSourceWork()
      val result = ImagesRule.merge(sierraWork, miroWorks :+ metsWork).data

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        miroWorks.map(_.data.images.head.location)
    }
  }

  describe("the flat image merging rule") {
    val testRule = new FlatImageMergeRule {
      override val isDefinedForTarget: WorkPredicate = _ => true
      override val isDefinedForSource: WorkPredicate = _ => true
    }

    it("creates images from every source") {
      val target = sierraDigitalSourceWork()
      val sources = (1 to 5).map(_ => createMiroWork)
      testRule(target, sources).get should have length 5
    }
  }

  def createInvisibleMetsSourceWorkWith(
    numImages: Int): Work.Invisible[Source] =
    createInvisibleMetsSourceWorkWith(images = (1 to numImages).map { _ =>
      createUnmergedMetsImage
    }.toList)
}
