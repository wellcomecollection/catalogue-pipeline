package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorksGenerators

class DisplayCollectionTest
    extends AnyFunSpec
    with Matchers
    with WorksGenerators {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path),
      collectionPath = Some(CollectionPath(path = path, level = Some(level))))

  it("creates a display tree with a path expanded") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val c = work("a/b/c", CollectionLevel.Item)
    val tree = Collection(List(a, b, c)).right.get
    DisplayCollection(tree, List("a/b/c")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Series")),
              work = Some(DisplayWork(b)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b/c", Some("Item")),
                    work = Some(DisplayWork(c)),
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
          level = Some(CollectionLevel.Item),
          label = Some("!!!"))))
    val tree = Collection(List(a, b)).right.get
    DisplayCollection(tree, List("a")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Item"), Some("!!!")),
              work = Some(DisplayWork(b)),
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
    val tree = Collection(List(a, b, c, d, e)).right.get
    DisplayCollection(tree, List("a/b/c", "a/d/e")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Series")),
              work = Some(DisplayWork(b)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b/c", Some("Item")),
                    work = Some(DisplayWork(c)),
                    children = Some(Nil)
                  )
                )
              )
            ),
            DisplayCollection(
              path = DisplayCollectionPath("a/d", Some("Series")),
              work = Some(DisplayWork(d)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/d/e", Some("Item")),
                    work = Some(DisplayWork(e)),
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
    val tree = Collection(List(a, b, c, d, e)).right.get
    DisplayCollection(tree, List("a/b/c")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Series")),
              work = Some(DisplayWork(b)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b/c", Some("Item")),
                    work = Some(DisplayWork(c)),
                    children = Some(Nil)
                  ),
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b/e", Some("Item")),
                    work = Some(DisplayWork(e)),
                    children = None
                  ),
                )
              )
            ),
            DisplayCollection(
              path = DisplayCollectionPath("a/d", Some("Item")),
              work = Some(DisplayWork(d)),
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
    val tree = Collection(List(a, b, c, d)).right.get
    DisplayCollection(tree, List("a/b")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Series")),
              work = Some(DisplayWork(b)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b/c", Some("Item")),
                    work = Some(DisplayWork(c)),
                    children = None
                  )
                )
              )
            ),
            DisplayCollection(
              path = DisplayCollectionPath("a/d", Some("Series")),
              work = Some(DisplayWork(d)),
              children = None
            )
          )
        )
      )
  }

  it("doesn't set expanded to paths sharing the same prefix as expanded") {
    val a = work("a", CollectionLevel.Collection)
    val b = work("a/b", CollectionLevel.Series)
    val b0 = work("a/b0", CollectionLevel.Series)
    val b1 = work("a/b0/b1", CollectionLevel.Item)
    val tree = Collection(List(a, b, b0, b1)).right.get
    DisplayCollection(tree, List("a/b0")) shouldBe
      DisplayCollection(
        path = DisplayCollectionPath("a", Some("Collection")),
        work = Some(DisplayWork(a)),
        children = Some(
          List(
            DisplayCollection(
              path = DisplayCollectionPath("a/b", Some("Series")),
              work = Some(DisplayWork(b)),
              children = None
            ),
            DisplayCollection(
              path = DisplayCollectionPath("a/b0", Some("Series")),
              work = Some(DisplayWork(b0)),
              children = Some(
                List(
                  DisplayCollection(
                    path = DisplayCollectionPath("a/b0/b1", Some("Item")),
                    work = Some(DisplayWork(b1))
                  )
                )
              )
            )
          )
        )
      )
  }
}
