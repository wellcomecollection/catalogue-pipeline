package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.ACursor
import io.circe.parser._
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.elasticsearch.IndexConfig
import weco.fixtures.LocalResources
import weco.json.utils.JsonAssertions

/**
  * These tests are to allow us to confirm that the JSON
  * from the Scala index config matches the work we do
  * in the rank app for search.
  *
  * The reason being is that the scala4s can _sometimes_
  * be hard to ensure it\s creating the right JSON,
  * which in and of itself is easy to understand.
  *
  * In essence the flow is test in rank,
  * get the JSON, paste it here and create the Scala.
  */
class SearchIndexConfigJsonTest
    extends AnyFunSpec
    with Matchers
    with LocalResources
    with JsonAssertions
    with IndexFixtures {
  def assertIndexConfigIsEquivalent(
    jsonFile: String,
    indexConfig: IndexConfig
  ): Assertion = {
    val fileJson =
      parse(readResource(jsonFile)).right.get.hcursor
    val fileMapping: String = fileJson.downField("mappings").focus.get.spaces2
    val fileSettings: String = fileJson.downField("settings").focus.get.spaces2
    withLocalElasticsearchIndex(config = indexConfig) { index =>
      val indexMapping =
        getJsonForIndex(
          index.name,
          "mappings",
          elasticClient
            .execute {
              getMapping(index.name)
            }
            .await
            .body
            .get
        ).focus.get.spaces2

      val indexSettings = getAnalysisSettingsOnly(
        responseBody = elasticClient
          .execute {
            getSettings(index.name)
          }
          .await
          .body
          .get,
        indexName = index.name
      )

      assertJsonStringsAreEqual(fileMapping, indexMapping)
      assertJsonStringsAreEqual(fileSettings, indexSettings)
    }
  }

  it("generates the correct works index config") {
    assertIndexConfigIsEquivalent(
      "WorksIndexConfig.json",
      WorksIndexConfig.indexed
    )
  }

  it("generates the correct images index config") {
    assertIndexConfigIsEquivalent(
      "ImagesIndexConfig.json",
      ImagesIndexConfig.indexed
    )
  }

  private def getAnalysisSettingsOnly(
    responseBody: String,
    indexName: String
  ): String = {
    val indexJson = getJsonForIndex(indexName, "settings", responseBody)
    val filteredJson = indexJson
      .downField("index")
      .withFocus(_.mapObject(_.filter {
        // We're only interested in looking at the analysis part of the settings
        case ("analysis", _) => true
        case _               => false
      }))
      .up
    filteredJson.focus.get.spaces2
  }

  private def getJsonForIndex(
    index: String,
    path: String,
    json: String
  ): ACursor =
    parse(json).right.get.hcursor
      .downField(index)
      .downField(path)
}
