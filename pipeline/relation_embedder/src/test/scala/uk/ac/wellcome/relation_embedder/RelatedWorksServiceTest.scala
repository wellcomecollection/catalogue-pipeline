package uk.ac.wellcome.relation_embedder

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import WorkState.Identified
import org.scalatest.Assertion
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures

class RelatedWorksServiceTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with WorkGenerators {

  def service(index: Index): PathQueryRelatedWorksService =
    new PathQueryRelatedWorksService(elasticClient, index)

  def work(path: String): Work.Visible[Identified] =
    identifiedWork(sourceIdentifier = createSourceIdentifierWith(value = path))
      .title(path)
      .collectionPath(CollectionPath(path = path))

  def storeWorks(index: Index, works: List[Work.Visible[Identified]] = works): Assertion =
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
      withLocalWorksIndex { index =>
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
      withLocalWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getOtherAffectedWorks(workA)) { result =>
          result should contain theSameElementsAs
            works
              .filter(_.state.canonicalId != workA.state.canonicalId)
              .map(_.sourceIdentifier)
        }
      }
    }

    it("Returns no affected works when work is not part of a collection") {
      withLocalWorksIndex { index =>
        val workX = identifiedWork()
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
      withLocalWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(work2)) { result =>
          result shouldBe
            RelatedWorks(
              parts = Some(List(RelatedWork(workD), RelatedWork(workE))),
              partOf = Some(
                List(RelatedWork(workA, RelatedWorks(partOf = Some(Nil))))),
              precededBy = Some(List(RelatedWork(work1))),
              succeededBy = Some(List(RelatedWork(work3), RelatedWork(work4))),
            )
        }
      }
    }

    it(
      "Retrieves a related works for the given path with ancestors sorted correctly") {
      withLocalWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(workF)) { relatedWorks =>
          relatedWorks shouldBe
            RelatedWorks(
              parts = Some(Nil),
              partOf = Some(
                List(
                  RelatedWork(
                    workE,
                    RelatedWorks.partOf(
                      RelatedWork(
                        work2,
                        RelatedWorks(
                          partOf = Some(
                            List(
                              RelatedWork(
                                workA,
                                RelatedWorks(partOf = Some(Nil)))
                            )
                          )
                        )
                      )
                    )
                  )
                )
              ),
              precededBy = Some(Nil),
              succeededBy = Some(Nil),
            )
        }
      }
    }

    it("Retrieves relations correctly from root position") {
      withLocalWorksIndex { index =>
        storeWorks(index)
        whenReady(service(index).getRelations(workA)) { relatedWorks =>
          relatedWorks shouldBe
            RelatedWorks(
              parts = Some(
                List(
                  RelatedWork(work1),
                  RelatedWork(work2),
                  RelatedWork(work3),
                  RelatedWork(work4))),
              partOf = Some(Nil),
              precededBy = Some(Nil),
              succeededBy = Some(Nil)
            )
        }
      }
    }

    it("Ignores missing ancestors") {
      withLocalWorksIndex { index =>
        storeWorks(index, List(workA, workB, workC, workD, workE, workF))
        whenReady(service(index).getRelations(workF)) { result =>
          result shouldBe
            RelatedWorks(
              parts = Some(Nil),
              partOf = Some(
                List(
                  RelatedWork(
                    workE,
                    RelatedWorks(
                      partOf = Some(
                        List(
                          RelatedWork(workA, RelatedWorks(partOf = Some(Nil)))
                        )
                      )
                    )
                  )
                )
              ),
              precededBy = Some(Nil),
              succeededBy = Some(Nil),
            )
        }
      }
    }

    it("Returns no related works when work is not part of a collection") {
      withLocalWorksIndex { index =>
        val workX = identifiedWork()
        storeWorks(index, List(workA, work1, workX))
        whenReady(service(index).getRelations(workX)) { result =>
          result shouldBe
            RelatedWorks(
              parts = Some(Nil),
              partOf = Some(Nil),
              precededBy = Some(Nil),
              succeededBy = Some(Nil)
            )
        }
      }
    }

    it("Sorts works consisting of paths with an alphanumeric mixture of tokens") {
      withLocalWorksIndex { index =>
        val workA = work("a")
        val workB1 = work("a/B1")
        val workB2 = work("a/B2")
        val workB10 = work("a/B10")
        storeWorks(index, List(workA, workB2, workB1, workB10))
        whenReady(service(index).getRelations(workA)) { result =>
          result shouldBe
            RelatedWorks(
              parts = Some(
                List(
                  RelatedWork(workB1),
                  RelatedWork(workB2),
                  RelatedWork(workB10))),
              partOf = Some(Nil),
              precededBy = Some(Nil),
              succeededBy = Some(Nil),
            )
        }
      }
    }
  }
}
