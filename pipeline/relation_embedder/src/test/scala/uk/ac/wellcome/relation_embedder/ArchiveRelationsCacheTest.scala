package uk.ac.wellcome.relation_embedder

import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorkGenerators

class ArchiveRelationsCacheTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators {

  def work(path: String) =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  val workA = work("a")
  val work1 = work("a/1")
  val workB = work("a/1/b")
  val workC = work("a/1/c")
  val work2 = work("a/2")
  val workD = work("a/2/d")
  val workE = work("a/2/e")
  val workF = work("a/2/e/f")
  val work3 = work("a/3")
  val work4 = work("a/4")

  val works =
    List(workA, workB, workC, workD, workE, workF, work4, work3, work2, work1)

  it(
    "Retrieves relations for the given path with children and siblings sorted correctly") {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(work2) shouldBe Relations(
      ancestors = List(Relation(workA, 0)),
      children = List(Relation(workD, 2), Relation(workE, 2)),
      siblingsPreceding = List(Relation(work1, 1)),
      siblingsSucceeding = List(Relation(work3, 1), Relation(work4, 1))
    )
  }

  it(
    "Retrieves relations for the given path with ancestors sorted correctly") {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workF) shouldBe Relations(
      ancestors =
        List(Relation(workA, 0), Relation(work2, 1), Relation(workE, 2)),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  it("Retrieves relations correctly from root position") {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workA) shouldBe Relations(
      ancestors = Nil,
      children = List(
        Relation(work1, 1),
        Relation(work2, 1),
        Relation(work3, 1),
        Relation(work4, 1)),
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
}

  it("Ignores missing ancestors") {
    val works = List(workA, workB, workC, workD, workE, workF)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workF) shouldBe Relations(
      ancestors = List(Relation(workE, 2)),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  it("Returns no related works when work is not part of a collection") {
    val workX = mergedWork()
    val works = List(workA, work1, workX)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workX) shouldBe Relations.none
  }

  it("Sorts works consisting of paths with an alphanumeric mixture of tokens") {
    val workA = work("a")
    val workB1 = work("a/B1")
    val workB2 = work("a/B2")
    val workB10 = work("a/B10")
    val works = List(workA, workB1, workB2, workB10)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workA) shouldBe Relations(
      ancestors = Nil,
      children = List(
        Relation(workB1, 1),
        Relation(workB2, 1),
        Relation(workB10, 1)
      ),
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }
}
