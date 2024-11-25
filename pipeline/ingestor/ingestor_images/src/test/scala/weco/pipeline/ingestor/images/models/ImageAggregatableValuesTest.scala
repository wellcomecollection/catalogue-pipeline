package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.ingestor.common.models.AggregatableField

class ImageAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData {

  it("creates aggregatable values from an image") {
    ImageAggregatableValues(testImage) shouldBe ImageAggregatableValues(
      licenses = List(
        AggregatableField(
          "cc-by",
          "Attribution 4.0 International (CC BY 4.0)"
        )
      ),
      contributors = List(
        AggregatableField("npanm646", "M.A.C.T"),
        AggregatableField(
          "wfkwqmmx",
          "McGlashan, Alan Fleming, 1898-1997"
        )
      ),
      genres = List(
        AggregatableField("h5fvmn9u", "Ink drawings"),
        AggregatableField("tgxvuh8x", "Drawings")
      ),
      subjects = List(
        AggregatableField("bse2dtxc", "Jungian psychology"),
        AggregatableField("hjw49bkh", "Dreams"),
        AggregatableField(
          "wfkwqmmx",
          "McGlashan, Alan Fleming, 1898-1997"
        )
      )
    )
  }

}
