package uk.ac.wellcome.platform.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, OptionValues, PrivateMethodTester}
import uk.ac.wellcome.models.work.internal.{
  IdState,
  SourceWorks,
  UnidentifiedInvisibleWork,
  WorkType
}
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators
import uk.ac.wellcome.platform.merger.rules.ImagesRule.FlatImageMergeRule
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

class ImagesRuleTest
    extends AnyFunSpec
    with Matchers
    with WorksWithImagesGenerators
    with PrivateMethodTester
    with OptionValues
    with Inspectors {
  describe("image creation rules") {
    it("creates 1 image from a single non-historical-library Miro work") {
      val miroWork = createMiroWorkWith(
        sourceIdentifier = createNonHistoricalLibraryMiroSourceIdentifier,
        images = List(createUnmergedMiroImage)
      )
      val result = ImagesRule.merge(miroWork).data

      result should have length 1
      result.head.location should be(miroWork.data.images.head.location)
      val source = result.head.source
      source shouldBe a[SourceWorks[_, _]]
      val sourceWorks = source.asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
      sourceWorks.canonicalWork.id.sourceIdentifier should be(
        miroWork.sourceIdentifier)
      sourceWorks.redirectedWork should be(None)
    }

    it("creates n images from n Miro works and a single Sierra work") {
      val n = 3
      val miroWorks = (1 to n)
        .map(_ => {
          val work = createMiroWork
          (work.sourceIdentifier -> work)
        })
        .toMap
      val sierraWork = createSierraDigitalWork
      val result = ImagesRule.merge(sierraWork, miroWorks.values.toList).data

      result.foreach { image =>
        val imageSource =
          image.source.asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
        val identifier = imageSource.canonicalWork.id.sourceIdentifier
        identifier shouldBe sierraWork.sourceIdentifier
        imageSource.redirectedWork shouldBe defined
        val redirectedSource = imageSource.redirectedWork.get
        val miroWork = miroWorks(redirectedSource.id.sourceIdentifier)
        image.location shouldBe miroWork.data.images.head.location
        redirectedSource.data shouldBe miroWork.data
      }
    }

    it(
      "creates n images from a METS work containing n images, and a single Sierra picture work") {
      val n = 5
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(numImages = n)
      val sierraPictureWork =
        createUnidentifiedSierraWorkWith(workType = Some(WorkType.Pictures))
      val result = ImagesRule.merge(sierraPictureWork, List(metsWork)).data

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location)
      result.map { image =>
        image.source
          .asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
          .canonicalWork
          .id
          .sourceIdentifier
      } should contain only sierraPictureWork.sourceIdentifier
    }

    it(
      "creates n + m images from m Miro works, a METS work containing n images, and a single Sierra picture work") {
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => createMiroWork).toList
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(numImages = n)
      val sierraPictureWork =
        createUnidentifiedSierraWorkWith(workType = Some(WorkType.Pictures))
      val result =
        ImagesRule.merge(sierraPictureWork, miroWorks :+ metsWork).data

      result should have length n + m
      result.map(_.location) should contain theSameElementsAs
        metsWork.data.images.map(_.location) ++
          miroWorks.map(_.data.images.head.location)
      result.map { image =>
        image.source
          .asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
          .canonicalWork
          .id
          .sourceIdentifier
      } should contain only sierraPictureWork.sourceIdentifier
    }

    it(
      "ignores METS images, but uses n Miro images, for a non-picture Sierra work") {
      val n = 3
      val metsWork = createUnidentifiedInvisibleMetsWorkWith(numImages = 3)
      val miroWorks = (1 to n).map(_ => createMiroWork).toList
      val sierraWork = createSierraDigitalWork
      val result = ImagesRule.merge(sierraWork, miroWorks :+ metsWork).data

      result should have length n
      result.map(_.location) should contain theSameElementsAs
        miroWorks.map(_.data.images.head.location)
      result.map { image =>
        image.source
          .asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
          .canonicalWork
          .id
          .sourceIdentifier
      } should contain only sierraWork.sourceIdentifier
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
      testRule(target, sources).get should have length 5
    }

    it("sets the target as the parentWork") {
      val target = createSierraDigitalWork
      val sources = (1 to 5).map(_ => createMiroWork)
      forAll(testRule.apply(target, sources).get) {
        _.source
          .asInstanceOf[SourceWorks[IdState.Identifiable, IdState.Unminted]]
          .canonicalWork
          .id
          .sourceIdentifier should be(target.sourceIdentifier)
      }
    }
  }

  def createUnidentifiedInvisibleMetsWorkWith(
    numImages: Int): UnidentifiedInvisibleWork =
    createUnidentifiedInvisibleMetsWorkWith(images = (1 to numImages).map { _ =>
      createUnmergedMetsImage
    }.toList)
}
