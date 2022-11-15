package weco.catalogue.internal_model.index

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.utils.JsonAssertions

class IndexMappingTest
  extends AnyFunSpec
  with Matchers
  with JsonAssertions {

  it("formats a strict mapping document containing the given properties") {
    val properties = """{"modifiedTime": {"type": "date"}}"""
    assertJsonStringsAreEqual(
      """{
        "properties": {"modifiedTime": {"type": "date"}},
        "_meta": {"model.versions.99": "5"},
        "dynamic": "strict"
        }""".stripMargin,
      IndexMapping(propertiesJson = properties, buildVersion = "99.5.RC13")
    )
  }
}
