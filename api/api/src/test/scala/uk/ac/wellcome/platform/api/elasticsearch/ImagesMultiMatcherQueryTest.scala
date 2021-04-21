package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.requests.searches.queries.QueryBuilderFn
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import uk.ac.wellcome.json.utils.JsonAssertions

class ImagesMultiMatcherQueryTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions {
  it("matches the JSON version of the query") {
    val fileJson =
      Source
        .fromResource("ImagesMultiMatcherQuery.json")
        .getLines
        .mkString

    val queryJson = QueryBuilderFn(ImagesMultiMatcher("{{query}}")).string()
    assertJsonStringsAreEqual(fileJson, queryJson)
  }
}
