package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{
  FunSpec,
  Inspectors,
  Matchers,
  OptionValues,
  PrivateMethodTester
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.platform.merger.fixtures.ImageFulltextAccess
import uk.ac.wellcome.platform.merger.rules.ImagesRule.FlatImageMergeRule
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

class ImagesRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with PrivateMethodTester
    with OptionValues
    with Inspectors
    with ImageFulltextAccess {
  describe("image creation rules") {
    it("creates 1 image from a 1 Miro work") {
      val miroWork = createMiroWork
      val result = ImagesRule.merge(miroWork).fieldData

      result should have length 1
      result.head.location should be(miroWork.data.images.head.location)
      result.head.data.parentWork.allSourceIdentifiers.head should
        be(miroWork.sourceIdentifier)
    }

    it("creates n images from n Miro works and a single Sierra work") {
      val n = 3
      val miroWorks = (1 to n).map(_ => createMiroWork).toList
      val sierraWork = createSierraDigitalWork
      val result = ImagesRule.merge(sierraWork, miroWorks).fieldData

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        miroWorks.map(_.data.images.head.location)
      result.flatMap(_.data.parentWork.allSourceIdentifiers) should contain only sierraWork.sourceIdentifier
    }

    it(
      "creates n images from a METS work containing n images, and a single Sierra picture work") {
      val n = 5
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(nImages = n)
      val sierraPictureWork =
        createUnidentifiedSierraWorkWith(workType = Some(WorkType.Pictures))
      val result = ImagesRule.merge(sierraPictureWork, List(metsWork)).fieldData

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location)
      result.flatMap(_.data.parentWork.allSourceIdentifiers) should contain only sierraPictureWork.sourceIdentifier
    }

    it(
      "creates n + m images from m Miro works, a METS work containing n images, and a single Sierra picture work") {
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => createMiroWork).toList
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(nImages = n)
      val sierraPictureWork =
        createUnidentifiedSierraWorkWith(workType = Some(WorkType.Pictures))
      val result =
        ImagesRule.merge(sierraPictureWork, miroWorks :+ metsWork).fieldData

      result should have length n + m
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location) ++
          miroWorks.map(_.data.images.head.location)
      result.flatMap(_.data.parentWork.allSourceIdentifiers) should contain only sierraPictureWork.sourceIdentifier
    }

    it(
      "ignores METS images, but uses n Miro images, for a non-picture Sierra work") {
      val n = 3
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(nImages = 3)
      val miroWorks = (1 to n).map(_ => createMiroWork).toList
      val sierraWork = createSierraDigitalWork
      val result = ImagesRule.merge(sierraWork, miroWorks :+ metsWork).fieldData

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        miroWorks.map(_.data.images.head.location)
      result.flatMap(_.data.parentWork.allSourceIdentifiers) should contain only sierraWork.sourceIdentifier
    }
  }

  describe("fulltext field population") {
    val work = createUnidentifiedWorkWith(
      title = Some("destructive durian"),
      description = Some("boisterous banana"),
      physicalDescription = Some("abrasive apple"),
      lettering = Some("ruminating rhubarb")
    )

    it("adds the title field") {
      createFulltext(List(work)).value should include(work.data.title.get)
    }

    it("adds the description field") {
      createFulltext(List(work)).value should include(work.data.description.get)
    }

    it("adds the physical description field") {
      createFulltext(List(work)).value should
        include(work.data.physicalDescription.get)
    }

    it("adds the lettering field") {
      createFulltext(List(work)).value should include(work.data.lettering.get)
    }

    it("adds the fields from all of the works passed") {
      val works = (0 to 5) map { i =>
        createUnidentifiedWorkWith(
          title = Some(s"title ${i}"),
          description = Some(s"description ${i}"),
          physicalDescription = Some(s"physicalDescription ${i}"),
          lettering = Some(s"lettering ${i}")
        )
      }
      val fullText = createFulltext(works)
      (0 to 5) foreach { i =>
        fullText.value should include(s"title ${i}")
        fullText.value should include(s"description ${i}")
        fullText.value should include(s"physicalDescription ${i}")
        fullText.value should include(s"lettering ${i}")
      }
    }
  }

  describe("the flat image merging rule") {
    val testRule = new FlatImageMergeRule {
      override val isDefinedForTarget: WorkPredicate = _ => true
      override val isDefinedForSource: WorkPredicate = _ => true
    }

    it("creates images from every source") {
      val target = createSierraDigitalWork
      val sources = (1 to 5).map(_ => createMiroWork)
      testRule.apply((target, sources)) should have length 5
    }

    it("sets the target as the parentWork") {
      val target = createSierraDigitalWork
      val sources = (1 to 5).map(_ => createMiroWork)
      forAll(testRule.apply((target, sources))) {
        _.data.parentWork.allSourceIdentifiers should
          contain(target.sourceIdentifier)
      }
    }
  }
}
