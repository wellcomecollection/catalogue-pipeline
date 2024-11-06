package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ImageFilterableValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData {

  it("creates filterable values from an image") {
    ImageFilterableValues(testImage) shouldBe ImageFilterableValues(
      locationsLicenseId = List("cc-by"),
      sourceContributorsAgentLabel =
        List("M.A.C.T", "McGlashan, Alan Fleming, 1898-1997"),
      sourceContributorsAgentId =
        List("npanm646", "wfkwqmmx"),
      sourceGenresLabel = List("Ink drawings", "Drawings"),
      sourceGenresConceptsId = List("h5fvmn9u", "tgxvuh8x"),
      sourceSubjectsLabel = List(
        "Jungian psychology",
        "Dreams",
        "McGlashan, Alan Fleming, 1898-1997"
      ),
      sourceSubjectsConceptsId =
        List("bse2dtxc", "hjw49bkh", "wfkwqmmx"),
      sourceProductionDatesRangeFrom = List(0)
    )
  }
}
