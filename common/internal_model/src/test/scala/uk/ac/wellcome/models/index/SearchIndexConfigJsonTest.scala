package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.handlers.index.CreateIndexContentBuilder
import com.sksamuel.elastic4s.json.JacksonBuilder
import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import uk.ac.wellcome.json.utils.JsonAssertions

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
    with JsonAssertions {

  it("generates the correct works index config") {
    val fileJson =
      Source
        .fromResource("WorksIndexConfig.json")
        .getLines
        .mkString

    val indexJson = JacksonBuilder.writeAsString(
      CreateIndexContentBuilder(
        CreateIndexRequest(
          "works",
          analysis = Some(IndexedWorkIndexConfig.analysis),
          mapping = Some(IndexedWorkIndexConfig.mapping.meta(Map()))
        )
      ).value)

    assertJsonStringsAreEqual(fileJson, indexJson)
  }

  it("generates the correct images index config") {
    val fileJson =
      Source
        .fromResource("ImagesIndexConfig.json")
        .getLines
        .mkString

    val indexJson = JacksonBuilder.writeAsString(
      CreateIndexContentBuilder(
        CreateIndexRequest(
          "images",
          analysis = Some(ImagesIndexConfig.ingested.analysis),
          mapping = Some(ImagesIndexConfig.ingested.mapping.meta(Map()))
        )
      ).value)

    assertJsonStringsAreEqual(fileJson, indexJson)
  }
}
