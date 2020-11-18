package uk.ac.wellcome.relation_embedder

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.Merged
import uk.ac.wellcome.models.work.internal._

class RelationsServiceTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with WorkGenerators
    with Akka {

  def service[R](index: Index)(implicit as: ActorSystem) =
    new PathQueryRelationsService(elasticClient, index, 10)

  def work(path: String) =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  def storeWorks(index: Index, works: List[Work[Merged]] = works): Assertion =
    insertIntoElasticsearch(index, works: _*)

  /** The following tests use works within this tree:
    *
    * A
    * |-------------
    * |  |         |
    * B  C         E
    * |  |------   |---
    * |  |  |  |   |  |
    * D  X  Y  Z   1  2
    *    |
    *    |--|
    *    |  |
    *    3  4
    */

  val workA = work("A")
  val workB = work("A/B")
  val workC = work("A/C")
  val workD = work("A/B/D")
  val workE = work("A/E")
  val workX = work("A/C/X")
  val workY = work("A/C/Y")
  val workZ = work("A/C/Z")
  val work1 = work("A/E/1")
  val work2 = work("A/E/2")
  val work3 = work("A/C/X/3")
  val work4 = work("A/C/X/4")

  val works =
    List(workA, workB, workC, workD, workE, workX, workY, workZ, work1, work2, work3, work4)

  describe("getAffectedWorks") {

    import Selector._

    it("Retrieves all affected works when batch consists of a whole tree") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Tree("A")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs works
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of single node") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Node("A/B")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs List(workB)
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of a nodes children") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Children("A/C")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs List(workX, workY, workZ)
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of a nodes descendents") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Descendents("A/C")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs List(workX, workY, workZ, work3, work4)
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of a mixture of selectors") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Node("A/E/2"), Descendents("A/C"), Children("A")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs List(
              workB,
              workC,
              workE,
              workX,
              workY,
              workZ,
              work2,
              work3,
              work4,
            )
          }
        }
      }
    }

    it("Ignores invisible works") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index, work("A/C/X/5").invisible() :: works)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Children("A/C/X"), Descendents("A/C/X"), Node("A/C/X/5")))
          whenReady(queryAffectedWorks(service(index), batch)) { result =>
            result should contain theSameElementsAs List(work3, work4)
          }
        }
      }
    }

    def queryAffectedWorks(service: RelationsService,
                           batch: Batch)(implicit as: ActorSystem) =
      service.getAffectedWorks(batch).runWith(Sink.seq[Work[Merged]])

  }

  describe("getWholeTree") {

    import Selector._

    val batch = Batch("A", List(Children("A/B"), Node("A/C/X")))

    it("Retrieves all works in archive") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          storeWorks(index, works)
          whenReady(queryWholeTree(service(index), batch)) { archiveWorks =>
            archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    it("Ignores works in other archvies") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          storeWorks(index, work("other/archive") :: works)
          whenReady(queryWholeTree(service(index), batch)) { archiveWorks =>
            archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    it("Ignores invisible works") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          storeWorks(index, work("A/Invisible").invisible() :: works)
          whenReady(queryWholeTree(service(index), batch)) { archiveWorks =>
            archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    def queryWholeTree(service: RelationsService,
                       batch: Batch)(implicit as: ActorSystem) =
      service.getWholeTree(batch).runWith(Sink.seq[Work[Merged]])
  }
}
