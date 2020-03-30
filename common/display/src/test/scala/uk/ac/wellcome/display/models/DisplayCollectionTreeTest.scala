package uk.ac.wellcome.display.models

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.display.models.v2._
import uk.ac.wellcome.models.work.generators.WorksGenerators

class DisplayCollectionTreeTest
    extends FunSpec
    with Matchers
    with WorksGenerators {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(CollectionPath(path = path, level = level)))

  it("creates a display tree with a path expanded") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val tree = CollectionTree(List(a, b, c)).right.get
    DisplayCollectionTree(tree, List("a/b/c")) shouldBe
      DisplayCollectionTree(
        path = DisplayCollectionPath("a", "Collection"),
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/b", "Series"),
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/b/c", "Item"),
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
    val a = work("a", CollectionLevel.Collection)
    val b = createIdentifiedWorkWith(
      collectionPath = Some(
        CollectionPath(
          path = "a/b",
          level = CollectionLevel.Item,
          label = Some("!!!"))))
    val tree = CollectionTree(List(a, b)).right.get
    DisplayCollectionTree(tree, List("a")) shouldBe
      DisplayCollectionTree(
        path = DisplayCollectionPath("a", "Collection"),
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/b", "Item", Some("!!!")),
              work = DisplayWorkV2(b),
            )
          )
        )
      )
  }

  it("creates a display tree with multiple paths expanded") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val d = work("a/d", CollectionLevel.Series)
    val e = work("a/d/e", CollectionLevel.Item)
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b/c", "a/d/e")) shouldBe
      DisplayCollectionTree(
        path = DisplayCollectionPath("a", "Collection"),
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/b", "Series"),
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/b/c", "Item"),
                    work = DisplayWorkV2(c),
                    children = Some(Nil)
                  )
                )
              )
            ),
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/d", "Series"),
              work = DisplayWorkV2(d),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/d/e", "Item"),
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
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val d = work("a/d", CollectionLevel.Item)
    val e = work("a/b/e", CollectionLevel.Item)
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b/c")) shouldBe
      DisplayCollectionTree(
        path = DisplayCollectionPath("a", "Collection"),
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/b", "Series"),
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/b/c", "Item"),
                    work = DisplayWorkV2(c),
                    children = Some(Nil)
                  ),
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/b/e", "Item"),
                    work = DisplayWorkV2(e),
                    children = None
                  ),
                )
              )
            ),
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/d", "Item"),
              work = DisplayWorkV2(d),
              children = None
            )
          )
        )
      )
  }

  it("sets non-expanded paths as None rather than empty list") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val d = work("a/d", CollectionLevel.Series)
    val e = work("a/d/e", CollectionLevel.Item)
    val tree = CollectionTree(List(a, b, c, d, e)).right.get
    DisplayCollectionTree(tree, List("a/b")) shouldBe
      DisplayCollectionTree(
        path = DisplayCollectionPath("a", "Collection"),
        work = DisplayWorkV2(a),
        children = Some(
          List(
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/b", "Series"),
              work = DisplayWorkV2(b),
              children = Some(
                List(
                  DisplayCollectionTree(
                    path = DisplayCollectionPath("a/b/c", "Item"),
                    work = DisplayWorkV2(c),
                    children = None
                  )
                )
              )
            ),
            DisplayCollectionTree(
              path = DisplayCollectionPath("a/d", "Series"),
              work = DisplayWorkV2(d),
              children = None
            )
          )
        )
      )
  }
}
