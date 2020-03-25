package uk.ac.wellcome.models.work.internal

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators

class CollectionTreeTest extends FunSpec with Matchers with WorksGenerators {

  def work(path: String) =
    createIdentifiedWorkWith(collection = Some(Collection(path = path)))

  it("creates a tree from a connected list of works") {
    val a = work("a")
    val b = work("a/b")
    val c = work("a/b/c")
    val d = work("a/d")
    val e = work("a/b/e")
    CollectionTree(List(b, d, a, c, e)) shouldBe Right(
      CollectionTree(
        path = "a",
        work = a,
        children = List(
          CollectionTree(
            path = "a/b",
            work = b,
            children = List(
              CollectionTree(
                path = "a/b/c",
                work = c
              ),
              CollectionTree(
                path = "a/b/e",
                work = e
              ),
            ),
          ),
          CollectionTree(
            path = "a/d",
            work = d
          )
        )
      )
    )
  }

  it("carries over collection labels to the tree") {
    val a = work("a")
    val b = createIdentifiedWorkWith(
      collection = Some(Collection(path = "a/b", label = Some("!!!")))
    )
    CollectionTree(List(a, b)) shouldBe Right(
      CollectionTree(
        path = "a",
        work = a,
        children = List(
          CollectionTree(
            path = "a/b",
            work = b,
            label = Some("!!!")
          )
        )
      )
    )
  }

  it("errors creating a tree when unconnected works") {
    val x = work("x")
    val y = work("x/y")
    val z = work("x/a/z")
    val result = CollectionTree(List(x, y, z))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Not all works in collection are connected to root 'x': x/a/z"
  }

  it("errors creating a tree when duplicate paths") {
    val x = work("x")
    val y = work("x/y")
    val z1 = work("x/y/z")
    val z2 = work("x/y/z")
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
    val x = work("x")
    val y = work("x/y")
    val z = createIdentifiedWorkWith(collection = None)
    val result = CollectionTree(List(x, y, z))
    result shouldBe a[Left[_, _]]
  }
}
