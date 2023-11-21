package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ImageAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData {

  it("creates aggregatable values from an image") {
    ImageAggregatableValues(testImage) shouldBe ImageAggregatableValues(
      licenses = List(
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}"""
      ),
      contributors = List(
        """{"id":"npanm646","label":"M.A.C.T","type":"Person"}""",
        """{"id":"wfkwqmmx","label":"McGlashan, Alan Fleming, 1898-1997","type":"Person"}"""
      ),
      genres = List(
        """{"label":"Ink drawings","concepts":[],"type":"Genre"}""",
        """{"label":"Drawings","concepts":[],"type":"Genre"}"""
      ),
      subjects = List(
        """{"label":"Jungian psychology","concepts":[],"type":"Subject"}""",
        """{"label":"Dreams","concepts":[],"type":"Subject"}""",
        """{"label":"McGlashan, Alan Fleming, 1898-1997","concepts":[],"type":"Subject"}"""
      )
    )
  }

}
