package weco.pipeline.relation_embedder

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.IndexFixturesOld
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.Work
import weco.pipeline.relation_embedder.fixtures.RelationGenerators
import weco.pipeline.relation_embedder.models._

class RelationsServiceTest
    extends AnyFunSpec
    with Matchers
    with IndexFixturesOld
    with RelationGenerators
    with Akka {

  def service(index: Index,
              completeTreeScroll: Int = 20,
              affectedWorksScroll: Int = 20)(implicit as: ActorSystem) =
    new PathQueryRelationsService(
      elasticClient = elasticClient,
      index = index,
      completeTreeScroll = completeTreeScroll,
      affectedWorksScroll = affectedWorksScroll
    )

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
    List(
      workA,
      workB,
      workC,
      workD,
      workE,
      workX,
      workY,
      workZ,
      work1,
      work2,
      work3,
      work4)

  describe("getAffectedWorks") {

    import Selector._

    it("Retrieves all affected works when batch consists of a complete tree") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Tree("A")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs works
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of single node") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Node("A/B")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs List(workB)
          }
        }
      }
    }

    it("Retrieves all affected works when batch consists of a nodes children") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Children("A/C")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs List(workX, workY, workZ)
          }
        }
      }
    }

    it(
      "Retrieves all affected works when batch consists of a nodes descendents") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Descendents("A/C")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs List(
              workX,
              workY,
              workZ,
              work3,
              work4)
          }
        }
      }
    }

    it(
      "Retrieves all affected works when batch consists of a mixture of selectors") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(
            rootPath = "A",
            List(Node("A/E/2"), Descendents("A/C"), Children("A")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs List(
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

    it("Retrieves all affected works across multiple scroll pages") {
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(rootPath = "A", List(Tree("A")))
          whenReady(queryAffectedWorks(
            service(index, affectedWorksScroll = 3),
            batch)) {
            _ should contain theSameElementsAs works
          }
        }
      }
    }

    it("Returns invisible works") {
      withLocalMergedWorksIndex { index =>
        val invisibleWork = work("A/C/X/5").invisible()
        insertIntoElasticsearch(index, invisibleWork :: works: _*)
        withActorSystem { implicit actorSystem =>
          val batch = Batch(
            rootPath = "A",
            List(Children("A/C/X"), Descendents("A/C/X"), Node("A/C/X/5")))
          whenReady(queryAffectedWorks(service(index), batch)) {
            _ should contain theSameElementsAs List(work3, work4, invisibleWork)
          }
        }
      }
    }

    def queryAffectedWorks(service: RelationsService,
                           batch: Batch)(implicit as: ActorSystem) =
      service.getAffectedWorks(batch).runWith(Sink.seq[Work[Merged]])

  }

  describe("getRelationTree") {

    import Selector._

    val batch = Batch("A", List(Children("A/B"), Node("A/C/X")))

    it("Retrieves all works in archive") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          insertIntoElasticsearch(index, works: _*)
          whenReady(queryRelationTree(service(index), batch)) {
            _ should contain theSameElementsAs works.map(toRelationWork)
          }
        }
      }
    }

    it("Ignores works in other archives") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          insertIntoElasticsearch(index, work("other/archive") :: works: _*)
          whenReady(queryRelationTree(service(index), batch)) {
            _ should contain theSameElementsAs works.map(toRelationWork)
          }
        }
      }
    }

    it("Ignores invisible works") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          insertIntoElasticsearch(
            index,
            work("A/Invisible").invisible() :: works: _*)
          whenReady(queryRelationTree(service(index), batch)) {
            _ should contain theSameElementsAs works.map(toRelationWork)
          }
        }
      }
    }

    it("handles gaps in a tree") {
      /*
       * If a path is specified where not all nodes correspond to a record,
       * A tree containing all the existing records should still be created.
       * */
      val works = List(
        work("x"),
        work("x/y/z")
      )
      val batch = Batch(
        rootPath = "x",
        selectors = List(Tree("x"))
      )
      val expected = works.map(toRelationWork)
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          insertIntoElasticsearch(index, works: _*)
          whenReady(queryRelationTree(service(index), batch)) {
            _ should contain theSameElementsAs expected
          }
        }
      }
    }
    def queryRelationTree(service: RelationsService,
                          batch: Batch)(implicit as: ActorSystem) =
      service.getRelationTree(batch).runWith(Sink.seq[RelationWork])
  }
}
