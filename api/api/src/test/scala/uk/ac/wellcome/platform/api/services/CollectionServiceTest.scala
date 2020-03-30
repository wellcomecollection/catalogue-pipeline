package uk.ac.wellcome.platform.api.services

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.{
  IdentifiersGenerators,
  ItemsGenerators,
  WorksGenerators
}

class CollectionServiceTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with ElasticsearchFixtures
    with IdentifiersGenerators
    with ItemsGenerators
    with WorksGenerators {

  val service = new CollectionService(elasticClient)

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(
        CollectionPath(path = path, level = level)
      ),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  def storeWorks(index: Index, works: List[IdentifiedWork] = works) =
    insertIntoElasticsearch(index, works: _*)

  val workA = work("a", CollectionLevel.Collection)
  val workB = work("a/b", CollectionLevel.Series)
  val workC = work("a/b/c", CollectionLevel.Item)
  val workD = work("a/b/d", CollectionLevel.Item)
  val workE = work("a/e", CollectionLevel.Series)
  val workF = work("a/e/f", CollectionLevel.Series)
  val workG = work("a/e/f/g", CollectionLevel.Item)
  val workX = work("x", CollectionLevel.Collection)
  val workY = work("x/y", CollectionLevel.Series)
  val workZ = work("x/oops/z", CollectionLevel.Item)

  val works =
    List(workA, workB, workC, workD, workE, workF, workG, workX, workY, workZ)

  it("Retrieves a tree with the given path and all ancestors expanded") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveTree(index, List("a/b"))) { result =>
        result shouldBe Right(
          Collection(
            path = CollectionPath("a", CollectionLevel.Collection),
            work = workA,
            children = List(
              Collection(
                path = CollectionPath("a/b", CollectionLevel.Series),
                work = workB,
                children = List(
                  Collection(
                    path = CollectionPath("a/b/c", CollectionLevel.Item),
                    work = workC),
                  Collection(
                    path = CollectionPath("a/b/d", CollectionLevel.Item),
                    work = workD)
                )
              ),
              Collection(
                path = CollectionPath("a/e", CollectionLevel.Series),
                work = workE),
            )
          )
        )
      }
    }
  }

  it("Retrieves a tree with multiple paths and their ancestors expanded") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveTree(index, List("a/b", "a/e/f"))) { result =>
        result shouldBe Right(
          Collection(
            path = CollectionPath("a", CollectionLevel.Collection),
            work = workA,
            children = List(
              Collection(
                path = CollectionPath("a/b", CollectionLevel.Series),
                work = workB,
                children = List(
                  Collection(
                    path = CollectionPath("a/b/c", CollectionLevel.Item),
                    work = workC),
                  Collection(
                    path = CollectionPath("a/b/d", CollectionLevel.Item),
                    work = workD)
                )
              ),
              Collection(
                path = CollectionPath("a/e", CollectionLevel.Series),
                work = workE,
                children = List(
                  Collection(
                    path = CollectionPath("a/e/f", CollectionLevel.Series),
                    work = workF,
                    children = List(
                      Collection(
                        path = CollectionPath("a/e/f/g", CollectionLevel.Item),
                        work = workG)
                    )
                  ),
                )
              ),
            )
          )
        )
      }
    }
  }

  it("Only expands by a single depth beyond the given path") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveTree(index, List("a"))) { result =>
        result shouldBe Right(
          Collection(
            path = CollectionPath("a", CollectionLevel.Collection),
            work = workA,
            children = List(
              Collection(
                path = CollectionPath("a/b", CollectionLevel.Series),
                work = workB,
              ),
              Collection(
                path = CollectionPath("a/e", CollectionLevel.Series),
                work = workE,
              )
            )
          )
        )
      }
    }
  }

  it("Fails creating a tree when incomplete data") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      whenReady(service.retrieveTree(index, List("x/oops"))) { result =>
        result shouldBe a[Left[_, _]]
        result.left.get.getMessage shouldBe "Not all works in collection are connected to root 'x': x/oops/z"
      }
    }
  }

  it("Fails creating a tree when duplicate paths") {
    withLocalWorksIndex { index =>
      storeWorks(index, work("a/e/f/g", CollectionLevel.Item) :: works)
      whenReady(service.retrieveTree(index, List("a/e/f"))) { result =>
        result shouldBe a[Left[_, _]]
        result.left.get.getMessage shouldBe "Tree contains duplicate paths: a/e/f/g"
      }
    }
  }

  it("Excludes larger fields from works stored in the tree") {
    withLocalWorksIndex { index =>
      val p = work("p", CollectionLevel.Collection) withData (_.copy(
        items = List(createIdentifiedItem)))
      val q = work("p/q", CollectionLevel.Item) withData (_.copy(
        notes = List(GeneralNote("hi"))))
      storeWorks(index, List(p, q))
      whenReady(service.retrieveTree(index, List("p/q"))) { result =>
        result shouldBe Right(
          Collection(
            path = CollectionPath("p", CollectionLevel.Collection),
            work = p.withData(_.copy(items = Nil)),
            children = List(
              Collection(
                path = CollectionPath("p/q", CollectionLevel.Item),
                work = q.withData(_.copy(notes = Nil)),
              )
            )
          )
        )
      }
    }
  }
}
