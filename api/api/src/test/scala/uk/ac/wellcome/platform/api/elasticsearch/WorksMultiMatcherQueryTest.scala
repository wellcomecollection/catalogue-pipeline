package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.requests.searches.queries.QueryBuilderFn
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import uk.ac.wellcome.json.utils.JsonAssertions

class WorksMultiMatcherQueryTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions {
  it("matches the JSON version of the query") {
    val fileJson =
      Source
        .fromResource("WorksMultiMatcherQuery.json")
        .getLines
        .mkString

    val queryJson = QueryBuilderFn(WorksMultiMatcher("{{query}}")).string()
    assertJsonStringsAreEqual(fileJson, queryJson)
  }
}
