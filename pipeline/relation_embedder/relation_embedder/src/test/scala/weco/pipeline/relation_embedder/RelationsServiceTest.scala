package weco.pipeline.relation_embedder

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pekko.fixtures.Pekko
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.Work
import weco.fixtures.TestWith
import weco.pipeline.relation_embedder.fixtures.RelationGenerators
import weco.pipeline.relation_embedder.models._

class RelationsServiceTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with RelationGenerators
    with Pekko {

  def service(
    index: Index,
    completeTreeScroll: Int = 20,
    affectedWorksScroll: Int = 20
  ) =
    new PathQueryRelationsService(
      elasticClient = elasticClient,
      index = index,
      completeTreeScroll = completeTreeScroll,
      affectedWorksScroll = affectedWorksScroll
    )

  /** The following tests use works within this tree:
    *
    * A \|------------- \| | | B C E \| |------ |--- \| | | | | | D X Y Z 1 2 \|
    * | -- |
    * |:---|
    * |    |
    * 3 4
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
      work4
    )
  protected def withLocalDenormalisedWorksIndexContaining[R](
    indexedWorks: Seq[Work[Merged]]
  )(
    testWith: TestWith[
      Index,
      R
    ]
  ): R = {
    withLocalDenormalisedWorksIndex {
      index =>
        insertIntoElasticsearch(index, indexedWorks: _*)
        testWith(index)
    }
  }

  describe("getAffectedWorks") {

    import Selector._

    def queryAffectedWorks(
      index: Index,
      batch: Batch,
      completeTreeScroll: Int = 20,
      affectedWorksScroll: Int = 20
    ): Seq[Work[Merged]] = withActorSystem {
      implicit actorSystem =>
        whenReady(
          service(
            index,
            completeTreeScroll = completeTreeScroll,
            affectedWorksScroll = affectedWorksScroll
          ).getAffectedWorks(batch).runWith(Sink.seq[Work[Merged]])
        ) {
          foundWorks: Seq[Work[Merged]] => foundWorks
        }
    }

    it("Retrieves all affected works when batch consists of a complete tree") {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryAffectedWorks(
            index,
            Batch(rootPath = "A", List(Tree("A")))
          ) should contain theSameElementsAs works
      }
    }

    it("Retrieves all affected works when batch consists of single node") {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryAffectedWorks(
            index,
            Batch(rootPath = "A", List(Node("A/B")))
          ) should contain theSameElementsAs List(workB)
      }
    }

    it("Retrieves all affected works when batch consists of a nodes children") {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryAffectedWorks(
            index,
            Batch(rootPath = "A", List(Children("A/C")))
          ) should contain theSameElementsAs List(workX, workY, workZ)
      }
    }

    it(
      "Retrieves all affected works when batch consists of a nodes descendents"
    ) {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryAffectedWorks(
            index,
            Batch(rootPath = "A", List(Descendents("A/C")))
          ) should contain theSameElementsAs List(
            workX,
            workY,
            workZ,
            work3,
            work4
          )
      }
    }

    it(
      "Retrieves all affected works when batch consists of a mixture of selectors"
    ) {
      withLocalDenormalisedWorksIndex {
        index =>
          insertIntoElasticsearch(index, works: _*)
          val batch = Batch(
            rootPath = "A",
            List(Node("A/E/2"), Descendents("A/C"), Children("A"))
          )
          queryAffectedWorks(
            index,
            batch
          ) should contain theSameElementsAs List(
            workB,
            workC,
            workE,
            workX,
            workY,
            workZ,
            work2,
            work3,
            work4
          )
      }
    }

    it("Retrieves all affected works across multiple scroll pages") {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryAffectedWorks(
            index,
            Batch(rootPath = "A", List(Tree("A"))),
            affectedWorksScroll = 3
          ) should contain theSameElementsAs works
      }
    }

    it("Returns invisible works") {
      val invisibleWork = work("A/C/X/5").invisible()
      withLocalDenormalisedWorksIndexContaining(invisibleWork :: works) {
        index =>
          val batch = Batch(
            rootPath = "A",
            List(Children("A/C/X"), Descendents("A/C/X"), Node("A/C/X/5"))
          )
          queryAffectedWorks(
            index,
            batch
          ) should contain theSameElementsAs List(
            work3,
            work4,
            invisibleWork
          )
      }
    }
  }

  describe("getRelationTree") {

    import Selector._

    val batch = Batch("A", List(Children("A/B"), Node("A/C/X")))

    it("Retrieves all works in archive") {
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryRelationTree(index, batch) should contain theSameElementsAs works
            .map(toRelationWork)
      }
    }

    it("Ignores works in other archives") {
      withLocalDenormalisedWorksIndexContaining(work("other/archive") :: works) {
        index =>
          queryRelationTree(index, batch) should contain theSameElementsAs works
            .map(toRelationWork)
      }
    }

    it("Ignores invisible works") {
      withLocalDenormalisedWorksIndexContaining(
        work("A/Invisible").invisible() :: works
      ) {
        index =>
          queryRelationTree(index, batch) should contain theSameElementsAs works
            .map(toRelationWork)
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
      withLocalDenormalisedWorksIndexContaining(works) {
        index =>
          queryRelationTree(
            index,
            batch
          ) should contain theSameElementsAs expected
      }
    }

    def queryRelationTree(index: Index, batch: Batch): Seq[RelationWork] =
      withActorSystem {
        implicit actorSystem: ActorSystem =>
          whenReady(
            service(index)
              .getRelationTree(batch)
              .runWith(Sink.seq[RelationWork])
          ) {
            relationWorks: Seq[RelationWork] => relationWorks
          }
      }
  }
}
