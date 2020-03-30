package uk.ac.wellcome.models.work.internal

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators

class CollectionTreeTest extends FunSpec with Matchers with WorksGenerators {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(CollectionPath(path = path, level = level)))

  it("creates a tree from a connected list of works") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val d = work("a/d", CollectionLevel.Series)
    val e = work("a/b/e", CollectionLevel.Item)
    CollectionTree(List(b, d, a, c, e)) shouldBe Right(
      CollectionTree(
        path = CollectionPath("a", CollectionLevel.Collection),
        work = a,
        children = List(
          CollectionTree(
            path = CollectionPath("a/b", CollectionLevel.Series),
            work = b,
            children = List(
              CollectionTree(
                path = CollectionPath("a/b/c", CollectionLevel.Item),
                work = c
              ),
              CollectionTree(
                path = CollectionPath("a/b/e", CollectionLevel.Item),
                work = e
              ),
            ),
          ),
          CollectionTree(
            path = CollectionPath("a/d", CollectionLevel.Series),
            work = d
          )
        )
      )
    )
  }

  it("carries over collection labels to the tree") {
    val a = work("a", CollectionLevel.Collection)
    val b = createIdentifiedWorkWith(
      collectionPath = Some(
        CollectionPath(
          path = "a/b",
          level = CollectionLevel.Item,
          label = Some("!!!")))
    )
    CollectionTree(List(a, b)) shouldBe Right(
      CollectionTree(
        path = CollectionPath("a", CollectionLevel.Collection),
        work = a,
        children = List(
          CollectionTree(
            path = CollectionPath("a/b", CollectionLevel.Item, Some("!!!")),
            work = b
          )
        )
      )
    )
  }

  it("errors creating a tree when unconnected works") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z = work("x/a/z", CollectionLevel.Item)
    val result = CollectionTree(List(x, y, z))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Not all works in collection are connected to root 'x': x/a/z"
  }

  it("errors creating a tree when duplicate paths") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z1 = work("x/y/z", CollectionLevel.Item)
    val z2 = work("x/y/z", CollectionLevel.Item)
    val result = CollectionTree(List(x, y, z1, z2))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Tree contains duplicate paths: x/y/z"
  }

  it("errors creating a tree when empty list") {
    val result = CollectionTree(Nil)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Cannot create empty tree"
  }

  it("errors creating a tree when not all works are part of a collection") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z = createIdentifiedWorkWith(collectionPath = None)
    val result = CollectionTree(List(x, y, z))
    result shouldBe a[Left[_, _]]
  }
}
