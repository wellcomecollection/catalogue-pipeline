package weco.pipeline.relation_embedder.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class CollectionPathSorterTest extends AnyFunSpec with Matchers with TableDrivenPropertyChecks {
  it("handles simple cases") {
    val testCases = Table(
      "paths",

      // Within a common prefix, shorter paths sort higher
      List("A", "A/B"),
      List("A", "A/B", "A/B/C"),

      // But shorter paths may appear later than another path if they don't have
      // a common prefix
      List("A/B/C", "B"),
      List("A/B/C", "B", "B/1"),
    )

    forAll(testCases) { paths =>
      // Pass in the list as a set so they don't pick up any implicit ordering
      // from the test spec.
      CollectionPathSorter.sortPaths(paths.toSet) shouldBe paths
    }
  }
}
