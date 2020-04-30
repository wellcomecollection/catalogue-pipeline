package uk.ac.wellcome.models.work.internal

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators

class CollectionTest extends FunSpec with Matchers with WorksGenerators {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(CollectionPath(path = path, level = Some(level))))

  it("creates a tree from a connected list of works") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val d = work("a/d", CollectionLevel.Series)
    val e = work("a/b/e", CollectionLevel.Item)
    Collection(List(b, d, a, c, e)) shouldBe Right(
      Collection(
        path = CollectionPath("a", Some(CollectionLevel.Collection)),
        work = Some(a),
        children = List(
          Collection(
            path = CollectionPath("a/b", Some(CollectionLevel.Series)),
            work = Some(b),
            children = List(
              Collection(
                path = CollectionPath("a/b/c", Some(CollectionLevel.Item)),
                work = Some(c)
              ),
              Collection(
                path = CollectionPath("a/b/e", Some(CollectionLevel.Item)),
                work = Some(e)
              ),
            ),
          ),
          Collection(
            path = CollectionPath("a/d", Some(CollectionLevel.Series)),
            work = Some(d)
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
          level = Some(CollectionLevel.Item),
          label = Some("!!!")))
    )
    Collection(List(a, b)) shouldBe Right(
      Collection(
        path = CollectionPath("a", Some(CollectionLevel.Collection)),
        work = Some(a),
        children = List(
          Collection(
            path =
              CollectionPath("a/b", Some(CollectionLevel.Item), Some("!!!")),
            work = Some(b)
          )
        )
      )
    )
  }

  it("creates a tree from an unconnected list of works when shared root") {
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val e = work("a/d/e", CollectionLevel.Item)
    Collection(List(b, c, e)) shouldBe Right(
      Collection(
        path = CollectionPath("a"),
        work = None,
        children = List(
          Collection(
            path = CollectionPath("a/b", Some(CollectionLevel.Series)),
            work = Some(b),
            children = List(
              Collection(
                path = CollectionPath("a/b/c", Some(CollectionLevel.Item)),
                work = Some(c)
              )
            ),
          ),
          Collection(
            path = CollectionPath("a/d"),
            work = None,
            children = List(
              Collection(
                path = CollectionPath("a/d/e", Some(CollectionLevel.Item)),
                work = Some(e)
              )
            )
          )
        )
      )
    )
  }

  it("errors creating a tree when works have different roots") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z = work("z/z/z", CollectionLevel.Item)
    val result = Collection(List(x, y, z))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Multiple root paths not permitted: x, z"
  }

  it("errors creating a tree when duplicate paths") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z1 = work("x/y/z", CollectionLevel.Item)
    val z2 = work("x/y/z", CollectionLevel.Item)
    val result = Collection(List(x, y, z1, z2))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Tree contains duplicate paths: x/y/z"
  }

  it("errors creating a tree when empty list") {
    val result = Collection(Nil)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage shouldBe "Cannot create empty tree"
  }

  it("errors creating a tree when not all works are part of a collection") {
    val x = work("x", CollectionLevel.Collection)
    val y = work("x/y", CollectionLevel.Series)
    val z = createIdentifiedWorkWith(collectionPath = None)
    val result = Collection(List(x, y, z))
    result shouldBe a[Left[_, _]]
  }
}
