package uk.ac.wellcome.display.models

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.display.models.v2.DisplayWorkV2
import uk.ac.wellcome.models.work.generators.WorksGenerators

class DisplayCollectionTreeTest
    extends FunSpec
    with Matchers
    with WorksGenerators {

  def work(path: String) =
    createIdentifiedWorkWith(collection = Some(Collection(path = path)))

  it("creates a display tree with a path expanded") {
    val a = work("a")
    val b = work("a/b")
    val c = work("a/b/c")
    val tree = CollectionTree(List(a, b, c)).right.get
    DisplayCollectionTree(tree, List("a/b/c")) shouldBe
      DisplayCollectionTree(
        path = "a",
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = "a/b",
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = "a/b/c",
                    work = DisplayWorkV2(c),
                    children = Some(Nil)
                  )
                )
              )
            )
          )
        )
      )
  }

  it("creates a display tree with labels") {
    val a = work("a")
    val b = createIdentifiedWorkWith(
      collection = Some(Collection(path = "a/b", label = Some("!!!"))))
    val tree = CollectionTree(List(a, b)).right.get
    DisplayCollectionTree(tree, List("a")) shouldBe
      DisplayCollectionTree(
        path = "a",
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = "a/b",
              label = Some("!!!"),
              work = DisplayWorkV2(b),
            )
          )
        )
      )
  }

  it("creates a display tree with multiple paths expanded") {
    val a = work("a")
    val b = work("a/b")
    val c = work("a/b/c")
    val d = work("a/d")
    val e = work("a/d/e")
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b/c", "a/d/e")) shouldBe
      DisplayCollectionTree(
        path = "a",
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = "a/b",
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = "a/b/c",
                    work = DisplayWorkV2(c),
                    children = Some(Nil)
                  )
                )
              )
            ),
            DisplayCollectionTree(
              path = "a/d",
              work = DisplayWorkV2(d),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = "a/d/e",
                    work = DisplayWorkV2(e),
                    children = Some(Nil)
                  )
                )
              )
            )
          )
        )
      )
  }

  it("creates a display tree with ancestor paths also expanded") {
    val a = work("a")
    val b = work("a/b")
    val c = work("a/b/c")
    val d = work("a/d")
    val e = work("a/b/e")
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b/c")) shouldBe
      DisplayCollectionTree(
        path = "a",
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = "a/b",
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = "a/b/c",
                    work = DisplayWorkV2(c),
                    children = Some(Nil)
                  ),
                  DisplayCollectionTree(
                    path = "a/b/e",
                    work = DisplayWorkV2(e),
                    children = None
                  ),
                )
              )
            ),
            DisplayCollectionTree(
              path = "a/d",
              work = DisplayWorkV2(d),
              children = None
            )
          )
        )
      )
  }

  it("sets non-expanded paths as None rather than empty list") {
    val a = work("a")
    val b = work("a/b")
    val c = work("a/b/c")
    val d = work("a/d")
    val e = work("a/d/e")
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b")) shouldBe
      DisplayCollectionTree(
        path = "a",
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = "a/b",
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = "a/b/c",
                    work = DisplayWorkV2(c),
                    children = None
                  )
                )
              )
            ),
            DisplayCollectionTree(
              path = "a/d",
              work = DisplayWorkV2(d),
              children = None
            )
          )
        )
      )
  }
}
