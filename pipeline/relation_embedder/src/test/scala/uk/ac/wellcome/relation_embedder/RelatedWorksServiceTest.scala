package uk.ac.wellcome.relation_embedder

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

class RelatedWorksServiceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ElasticsearchFixtures
    with IdentifiersGenerators
    with ItemsGenerators
    with WorksGenerators {

  def service(index: Index) =
    new PathQueryRelatedWorksService(elasticClient, index)

  def work(path: String) =
    createIdentifiedWorkWith(
      collectionPath = Some(
        CollectionPath(path = path)
      ),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  def storeWorks(index: Index, works: List[IdentifiedWork] = works) =
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

  it(
    "Retrieves a related works for the given path with children and siblings sorted correctly") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service(index)(work2)) { result =>
        result shouldBe
          RelatedWorks(
            parts = Some(List(RelatedWork(workD), RelatedWork(workE))),
            partOf = Some(List(RelatedWork(workA, RelatedWorks(partOf = Some(Nil))))),
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
      whenReady(service(index)(workF)) { relatedWorks =>
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
      }
    }
  }

  it("Retrieves relations correctly from root position") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service(index)(workA)) { relatedWorks =>
        relatedWorks shouldBe
          RelatedWorks(
            parts = Some(List(
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
      whenReady(service(index)(workF)) { result =>
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
      val workX = createIdentifiedWork
      storeWorks(index, List(workA, work1, workX))
      whenReady(service(index)(workX)) { result =>
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
      whenReady(service(index)(workA)) { result =>
        result shouldBe
          RelatedWorks(
            parts = Some(List(
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
