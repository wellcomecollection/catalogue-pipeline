package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.ingestor.common.models.AggregatableIdLabel

class ImageAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData {

  it("creates aggregatable values from an image") {
    ImageAggregatableValues(testImage) shouldBe ImageAggregatableValues(
      licenses = List(
        AggregatableIdLabel(
          Some("cc-by"),
          "Attribution 4.0 International (CC BY 4.0)"
        )
      ),
      contributors = List(
        AggregatableIdLabel(Some("npanm646"), "M.A.C.T"),
        AggregatableIdLabel(
          Some("wfkwqmmx"),
          "McGlashan, Alan Fleming, 1898-1997"
        )
      ),
      genres = List(
        AggregatableIdLabel(Some("h5fvmn9u"), "Ink drawings"),
        AggregatableIdLabel(Some("tgxvuh8x"), "Drawings")
      ),
      subjects = List(
        AggregatableIdLabel(Some("bse2dtxc"), "Jungian psychology"),
        AggregatableIdLabel(Some("hjw49bkh"), "Dreams"),
        AggregatableIdLabel(
          Some("wfkwqmmx"),
          "McGlashan, Alan Fleming, 1898-1997"
        )
      )
    )
  }

}
