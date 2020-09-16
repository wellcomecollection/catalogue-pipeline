package uk.ac.wellcome.platform.api.services

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.{
  IdentifiersGenerators,
  ItemsGenerators,
  WorksGenerators
}
import WorkState.Identified

class RelatedWorkServiceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ElasticsearchFixtures
    with IdentifiersGenerators
    with ItemsGenerators
    with WorksGenerators {

  val service = new RelatedWorkService(new ElasticsearchService(elasticClient))

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(
        CollectionPath(path = path, level = Some(level))
      ),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  def storeWorks(index: Index, works: List[Work[Identified]] = works) =
    insertIntoElasticsearch(index, works: _*)

  val workA = work("a", CollectionLevel.Collection)
  val work1 = work("a/1", CollectionLevel.Series)
  val workB = work("a/1/b", CollectionLevel.Item)
  val workC = work("a/1/c", CollectionLevel.Item)
  val work2 = work("a/2", CollectionLevel.Series)
  val workD = work("a/2/d", CollectionLevel.Series)
  val workE = work("a/2/e", CollectionLevel.Item)
  val workF = work("a/2/e/f", CollectionLevel.Item)
  val work3 = work("a/3", CollectionLevel.Item)
  val work4 = work("a/4", CollectionLevel.Item)

  val works =
    List(workA, workB, workC, workD, workE, workF, work4, work3, work2, work1)

  it(
    "Retrieves a related works for the given path with children and siblings sorted correctly") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveRelatedWorks(index, work2)) { result =>
        result shouldBe Right(
          RelatedWorks(
            parts = Some(List(RelatedWork(workD), RelatedWork(workE))),
            partOf =
              Some(List(RelatedWork(workA, RelatedWorks(partOf = Some(Nil))))),
            precededBy = Some(List(RelatedWork(work1))),
            succeededBy = Some(List(RelatedWork(work3), RelatedWork(work4))),
          )
        )
      }
    }
  }

  it("Retrieves a related works for the given path with nested ancestors") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveRelatedWorks(index, workF)) { result =>
        result shouldBe Right(
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
                            RelatedWork(workA, RelatedWorks(partOf = Some(Nil)))
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
        )
      }
    }
  }

  it("Retrieves relations correctly from root position") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveRelatedWorks(index, workA)) { result =>
        result.right.get shouldBe
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
      whenReady(service.retrieveRelatedWorks(index, workF)) { result =>
        result shouldBe Right(
          RelatedWorks(
            parts = Some(Nil),
            partOf = Some(
              List(
                RelatedWork(
                  workE,
                  RelatedWorks.partOf(
                    RelatedWork(
                      workA,
                      RelatedWorks(partOf = Some(Nil))
                    )
                  )
                )
              )
            ),
            precededBy = Some(Nil),
            succeededBy = Some(Nil),
          )
        )
      }
    }
  }

  it("Only returns core fields on related works") {
    withLocalWorksIndex { index =>
      val workP = work("p", CollectionLevel.Collection) withData (
        _.copy[DataState.Identified](items = List(createIdentifiedItem))
      )
      val workQ = work("p/q", CollectionLevel.Series) withData (
        _.copy[DataState.Identified](notes = List(GeneralNote("hi")))
      )
      val workR = work("p/q/r", CollectionLevel.Item)
      storeWorks(index, List(workP, workQ, workR))
      whenReady(service.retrieveRelatedWorks(index, workR)) { result =>
        result shouldBe Right(
          RelatedWorks(
            parts = Some(Nil),
            partOf = Some(
              List(
                RelatedWork(
                  workQ.withData(_.copy(notes = Nil)),
                  RelatedWorks.partOf(
                    RelatedWork(
                      workP.withData(_.copy(items = Nil)),
                      RelatedWorks(partOf = Some(Nil))
                    )
                  )
                ),
              )),
            precededBy = Some(Nil),
            succeededBy = Some(Nil),
          )
        )
      }
    }
  }

  it("Returns no related works when work is not part of a collection") {
    withLocalWorksIndex { index =>
      val workX = createIdentifiedWork
      storeWorks(index, List(workA, work1, workX))
      whenReady(service.retrieveRelatedWorks(index, workX)) { result =>
        result shouldBe Right(RelatedWorks.nil)
      }
    }
  }

  it("Sorts works consisting of paths with an alphanumeric mixture of tokens") {
    withLocalWorksIndex { index =>
      val workA = work("a", CollectionLevel.Collection)
      val workB1 = work("a/B1", CollectionLevel.Series)
      val workB2 = work("a/B2", CollectionLevel.Item)
      val workB10 = work("a/B10", CollectionLevel.Item)
      storeWorks(index, List(workA, workB2, workB1, workB10))
      whenReady(service.retrieveRelatedWorks(index, workA)) { result =>
        result shouldBe Right(
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
        )
      }
    }
  }
}
