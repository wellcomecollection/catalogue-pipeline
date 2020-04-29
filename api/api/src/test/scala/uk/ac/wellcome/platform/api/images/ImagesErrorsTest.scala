package uk.ac.wellcome.platform.api.images

class ImagesErrorsTest extends ApiImagesTestBase {
  describe("returns a 404 for missing resources") {
    it("looking up an image that doesn't exist") {
      val id = "blahblah"
      assertIsNotFound(
        s"/images/$id",
        description = s"Image not found for identifier $id"
      )
    }
  }
}
