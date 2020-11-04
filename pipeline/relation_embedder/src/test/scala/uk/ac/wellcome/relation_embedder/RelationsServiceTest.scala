package uk.ac.wellcome.relation_embedder

import scala.concurrent.ExecutionContext.Implicits.global

import com.sksamuel.elastic4s.Index
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.Assertion

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.Implicits._
import WorkState.Merged

class RelationsServiceTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with WorkGenerators {

  def service(index: Index) =
    new PathQueryRelationsService(elasticClient, index)

  def work(path: String) =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  def storeWorks(index: Index, works: List[Work[Merged]] = works): Assertion =
    insertIntoElasticsearch(index, works: _*)

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

  describe("getOtherAffectedWorks") {
    it("Retrieves all affected works") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getOtherAffectedWorks(workE)) { result =>
          result should contain theSameElementsAs List(
            work2.sourceIdentifier,
            workD.sourceIdentifier,
            workF.sourceIdentifier,
          )
        }
      }
    }

    it(
      "Retrieves the whole remaining tree when getting affected works from root position") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getOtherAffectedWorks(workA)) { result =>
          result should contain theSameElementsAs
            works
              .filter(_.state.sourceIdentifier != workA.state.sourceIdentifier)
              .map(_.sourceIdentifier)
        }
      }
    }

    it("Returns no affected works when work is not part of a collection") {
      withLocalMergedWorksIndex { index =>
        val workX = mergedWork()
        storeWorks(index, List(workA, work1, workX))
        whenReady(service(index).getOtherAffectedWorks(workX)) { result =>
          result shouldBe Nil
        }
      }
    }
  }

  describe("getRelations") {
    it(
      "Retrieves a related works for the given path with children and siblings sorted correctly") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(work2)) { relations =>
          relations shouldBe Relations(
            ancestors = List(Relation(workA, 0)),
            children = List(Relation(workD, 2), Relation(workE, 2)),
            siblingsPreceding = List(Relation(work1, 1)),
            siblingsSucceeding = List(Relation(work3, 1), Relation(work4, 1))
          )
        }
      }
    }

    it(
      "Retrieves a related works for the given path with ancestors sorted correctly") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(workF)) { relations =>
          relations shouldBe Relations(
            ancestors =
              List(Relation(workA, 0), Relation(work2, 1), Relation(workE, 2)),
            children = Nil,
            siblingsPreceding = Nil,
            siblingsSucceeding = Nil
          )
        }
      }
    }

    it("Retrieves relations correctly from root position") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(workA)) { relations =>
          relations shouldBe Relations(
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
      }
    }

    it("Ignores missing ancestors") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index, List(workA, workB, workC, workD, workE, workF))
        whenReady(service(index).getRelations(workF)) { relations =>
          relations shouldBe Relations(
            ancestors = List(
              Relation(workA, 0),
              Relation(workE, 1),
            ),
            children = Nil,
            siblingsPreceding = Nil,
            siblingsSucceeding = Nil
          )
        }
      }
    }

    it("Filters out invisible works") {
      withLocalWorksIndex { index =>
        val workP = work("p")
        val workQ = work("p/q").invisible()
        val workR = work("p/r")
        storeWorks(index, List(workP, workQ, workR))
        whenReady(service(index).getRelations(workP)) { relations =>
          relations shouldBe Relations(
            children = List(Relation(workR, 1)),
            ancestors = Nil,
            siblingsPreceding = Nil,
            siblingsSucceeding = Nil
          )
        }
      }
    }

    it("Returns no related works when work is not part of a collection") {
      withLocalMergedWorksIndex { index =>
        val workX = mergedWork()
        storeWorks(index, List(workA, work1, workX))
        whenReady(service(index).getRelations(workX)) { relations =>
          relations shouldBe Relations(
            ancestors = Nil,
            children = Nil,
            siblingsPreceding = Nil,
            siblingsSucceeding = Nil
          )
        }
      }
    }

    it("Sorts works consisting of paths with an alphanumeric mixture of tokens") {
      withLocalMergedWorksIndex { index =>
        val workA = work("a")
        val workB1 = work("a/B1")
        val workB2 = work("a/B2")
        val workB10 = work("a/B10")
        storeWorks(index, List(workA, workB2, workB1, workB10))
        whenReady(service(index).getRelations(workA)) { relations =>
          relations shouldBe Relations(
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
    }
  }
}
