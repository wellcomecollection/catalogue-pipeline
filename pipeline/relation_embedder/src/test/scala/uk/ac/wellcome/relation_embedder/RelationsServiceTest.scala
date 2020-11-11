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
        withActorSystem { implicit as =>
          whenReady(queryAffectedWorks(service(index), workE)) { result =>
            result should contain theSameElementsAs List(
              work2,
              workD,
              workF,
            )
          }
        }
      }
    }

    it(
      "Retrieves the whole remaining tree when getting affected works from root position") {
      withLocalMergedWorksIndex { index =>
        storeWorks(index)
        withActorSystem { implicit as =>
          whenReady(queryAffectedWorks(service(index), workA)) { result =>
            result should contain theSameElementsAs
              works
                .filter(
                  _.state.sourceIdentifier != workA.state.sourceIdentifier)
          }
        }
      }
    }

    it("Returns no affected works when work is not part of a collection") {
      withLocalMergedWorksIndex { index =>
        val workX = mergedWork()
        storeWorks(index, List(workA, work1, workX))
        withActorSystem { implicit as =>
          whenReady(queryAffectedWorks(service(index), workX)) { result =>
            result shouldBe Nil
          }
        }
      }
    }

    it("Ignores invisible works") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit as =>
          storeWorks(index, work("a/2/invisible").invisible() :: works)
          whenReady(queryAffectedWorks(service(index), workE)) { result =>
            result should contain theSameElementsAs List(
              work2,
              workD,
              workF,
            )
          }
        }
      }
    }

    def queryAffectedWorks(service: RelationsService,
                           work: Work[Merged])(implicit as: ActorSystem) =
      service.getOtherAffectedWorks(work).runWith(Sink.seq[Work[Merged]])
  }

  describe("getAllWorksInArchive") {
    it("Retrieves all works in archive") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit as =>
          storeWorks(index, work("other/archive") :: works)
          whenReady(queryWorksInArchive(service(index), work2)) {
            archiveWorks =>
              archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    it("Retrieves all works in archive from root position") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit as =>
          storeWorks(index, work("other/archive") :: works)
          whenReady(queryWorksInArchive(service(index), workA)) {
            archiveWorks =>
              archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    it("Ignores invisible works") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit as =>
          storeWorks(index, work("a/invisible").invisible() :: works)
          whenReady(queryWorksInArchive(service(index), workE)) {
            archiveWorks =>
              archiveWorks should contain theSameElementsAs works
          }
        }
      }
    }

    def queryWorksInArchive(service: RelationsService,
                            work: Work[Merged])(implicit as: ActorSystem) =
      service.getAllWorksInArchive(work).runWith(Sink.seq[Work[Merged]])
  }
}
