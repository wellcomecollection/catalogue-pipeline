package weco.pipeline.relation_embedder.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class CollectionPathSorterTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
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
      // Numbers sort higher than letters
      List("A/B/1", "A/B/A"),
      // Sort within runs of letters/numbers
      List("A/B/1", "A/B/2", "A/B/3"),
      List("A/B/a", "A/B/b", "A/B/c"),
      // A suffix added after a letter
      List("A/1", "A/1a", "A/1b", "A/2", "A/10a", "A/10b", "A/11a"),
    )

    forAll(testCases) { paths =>
      // Pass in the list as a set so they don't pick up any implicit ordering
      // from the test spec.
      CollectionPathSorter.sortPaths(paths.toSet) shouldBe paths
    }
  }
}
