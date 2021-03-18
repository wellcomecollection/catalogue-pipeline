package weco.catalogue.internal_model.image

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators

class ParentWorkTest extends AnyFunSpec with Matchers with ImageGenerators {
  import ParentWork._

  describe("toParentWork") {
    it("removes the imageData from a merged work") {
      val w =
        mergedWork()
          .imageData((1 to 3).map { _ =>
            createImageData.toIdentified
          }.toList)

      w.toParentWork.data.imageData shouldBe empty
    }

    it("removes the imageData from an identified work") {
      val w =
        identifiedWork()
          .imageData((1 to 3).map { _ =>
            createImageData.toIdentified
          }.toList)

      w.toParentWork.data.imageData shouldBe empty
    }
  }
}
