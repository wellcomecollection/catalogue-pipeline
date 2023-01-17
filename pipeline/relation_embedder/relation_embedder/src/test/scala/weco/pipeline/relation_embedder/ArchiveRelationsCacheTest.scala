package weco.pipeline.relation_embedder

import org.apache.commons.io.IOUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.Inspectors
import weco.catalogue.internal_model.work.{Relation, Relations}
import weco.pipeline.relation_embedder.fixtures.RelationGenerators
import weco.pipeline.relation_embedder.models.ArchiveRelationsCache

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class ArchiveRelationsCacheTest
    extends AnyFunSpec
    with Matchers
    with Inspectors
    with RelationGenerators {

  val workA = work("a")
  val work1 = work("a/1")
  val workB = work("a/1/b")
  val workC = work("a/1/c", isAvailableOnline = true)
  val work2 = work("a/2")
  val workD = work("a/2/d")
  val workE = work("a/2/e")
  val workF = work("a/2/e/f")
  val work3 = work("a/3")
  val work4 = work("a/4")

  val relA = Relation(workA, depth = 0, numChildren = 4, numDescendents = 9)
  val rel1 = Relation(work1, depth = 1, numChildren = 2, numDescendents = 2)
  val relB = Relation(workB, depth = 2, numChildren = 0, numDescendents = 0)
  val relC = Relation(workC, depth = 2, numChildren = 0, numDescendents = 0)
  val rel2 = Relation(work2, depth = 1, numChildren = 2, numDescendents = 3)
  val relD = Relation(workD, depth = 2, numChildren = 0, numDescendents = 0)
  val relE = Relation(workE, depth = 2, numChildren = 1, numDescendents = 1)
  val relF = Relation(workF, depth = 3, numChildren = 0, numDescendents = 0)
  val rel3 = Relation(work3, depth = 1, numChildren = 0, numDescendents = 0)
  val rel4 = Relation(work4, depth = 1, numChildren = 0, numDescendents = 0)

  val works =
    List(workA, workB, workC, workD, workE, workF, work4, work3, work2, work1)
      .map(toRelationWork)

  it(
    "Retrieves relations for the given path with children and siblings sorted correctly"
  ) {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(work2) shouldBe Relations(
      ancestors = List(relA),
      children = List(relD, relE),
      siblingsPreceding = List(rel1),
      siblingsSucceeding = List(rel3, rel4)
    )
  }

  it("Retrieves relations for the given path with ancestors sorted correctly") {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workF) shouldBe Relations(
      ancestors = List(relA, rel2, relE),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  it("Retrieves relations correctly from root position") {
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workA) shouldBe Relations(
      ancestors = Nil,
      children = List(rel1, rel2, rel3, rel4),
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  it("Ignores missing ancestors") {
    val works = List(workA, workB, workC, workD, workE, workF)
      .map(toRelationWork)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workF) shouldBe Relations(
      ancestors = List(relE),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  it("Returns no related works when work is not part of a collection") {
    val workX = mergedWork()
    val works = List(workA, work1, workX).map(toRelationWork)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workX) shouldBe Relations.none
  }

  it("Returns no relations when missing parent work (e.g if it is not visible)") {
    val works = List(workA, workD, workE, workF).map(toRelationWork)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workB) shouldBe Relations.none
  }

  it("Sorts works consisting of paths with an alphanumeric mixture of tokens") {
    val workA = work("a")
    val workB1 = work("a/B1")
    val workB2 = work("a/B2")
    val workB10 = work("a/B10")
    val works = List(workA, workB1, workB2, workB10).map(toRelationWork)
    val relationsCache = ArchiveRelationsCache(works)
    relationsCache(workA) shouldBe Relations(
      ancestors = Nil,
      children = List(
        Relation(workB1, depth = 1, numChildren = 0, numDescendents = 0),
        Relation(workB2, depth = 1, numChildren = 0, numDescendents = 0),
        Relation(workB10, depth = 1, numChildren = 0, numDescendents = 0)
      ),
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
  }

  // This is a real set of nearly 7000 paths from SAFPA.  This test is less focused on
  // the exact result, more that it returns in a reasonable time.
  //
  // Some refactoring of the relation embedder code accidentally made the childMapping
  // explode in runtime, effectively breaking the relation embedder for large collections.
  //
  // The exact timeout on the Future isn't important and can be adjusted slightly if
  // it's a bit slow on CI, as long as it's not ridiculous.
  it("finds the size in a reasonable time") {
    val paths = IOUtils
      .resourceToString("/paths.txt", StandardCharsets.UTF_8)
      .split("\n")

    val relations = paths.map { p =>
      toRelationWork(work(p))
    }

    val cache = ArchiveRelationsCache(relations)

    val future = Future {
      cache.size
    }

    Await.result(future, atMost = 5.seconds)
  }
}
