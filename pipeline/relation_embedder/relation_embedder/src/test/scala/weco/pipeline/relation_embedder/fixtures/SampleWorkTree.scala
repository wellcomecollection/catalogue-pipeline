package weco.pipeline.relation_embedder.fixtures

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import weco.catalogue.internal_model.work.{
  Availability,
  Relation,
  Relations,
  Work
}
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.fixtures.TestWith

import scala.collection.mutable

trait SampleWorkTree extends IndexFixtures with RelationGenerators {

  def storeWorks(index: Index, works: List[Work[Merged]] = works): Assertion =
    insertIntoElasticsearch(index, works: _*)

  /** The following tests use works within this tree:
    * {{{
    * a
    * |---
    * |  |
    * 1  2
    * |  |---
    * |  |  |
    * b  c  d†
    *    |
    *    |
    *    e
    * }}}
    * d† is available online
    */
  val workA = work("a")
  val work1 = work("a/1")
  val workB = work("a/1/b")
  val work2 = work("a/2")
  val workC = work("a/2/c")
  val workD = work("a/2/d", isAvailableOnline = true)
  val workE = work("a/2/d/e")

  val relA = Relation(workA, depth = 0, numChildren = 2, numDescendents = 6)
  val rel1 = Relation(work1, depth = 1, numChildren = 1, numDescendents = 1)
  val relB = Relation(workB, depth = 2, numChildren = 0, numDescendents = 0)
  val rel2 = Relation(work2, depth = 1, numChildren = 2, numDescendents = 3)
  val relC = Relation(workC, depth = 2, numChildren = 0, numDescendents = 0)
  val relD = Relation(workD, depth = 2, numChildren = 1, numDescendents = 1)
  val relE = Relation(workE, depth = 3, numChildren = 0, numDescendents = 0)

  val relationsA = Relations(children = List(rel1, rel2))
  val relations1 = Relations(
    ancestors = List(relA),
    children = List(relB),
    siblingsSucceeding = List(rel2)
  )
  val relationsB = Relations(ancestors = List(relA, rel1))
  val relations2 = Relations(
    ancestors = List(relA),
    children = List(relC, relD),
    siblingsPreceding = List(rel1)
  )
  val relationsC =
    Relations(ancestors = List(relA, rel2), siblingsSucceeding = List(relD))
  val relationsD = Relations(
    ancestors = List(relA, rel2),
    children = List(relE),
    siblingsPreceding = List(relC)
  )
  val relationsE = Relations(ancestors = List(relA, rel2, relD))

  val works =
    List(workA, workB, workC, workD, workE, work2, work1)

  protected def relations(
    index: mutable.Map[String, Work[Denormalised]]
  ): Map[String, Relations] =
    index.map { case (key, work) => key -> work.state.relations }.toMap

  protected def availabilities(
    index: mutable.Map[String, Work[Denormalised]]
  ): Map[String, Set[Availability]] =
    index.map { case (key, work) => key -> work.state.availabilities }.toMap

  protected def withUpstreamIndex[R](works: List[Work[Merged]])(
    testWith: TestWith[
      Index,
      R
    ]
  ): R = {
    withLocalDenormalisedWorksIndex {
      denormalisedIndex: Index =>
        storeWorks(denormalisedIndex, works)
        testWith(denormalisedIndex)
    }
  }
}
