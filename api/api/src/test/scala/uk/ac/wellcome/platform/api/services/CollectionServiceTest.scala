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

  def collectionService(index: Index) =
    new CollectionService(elasticClient, index)

  def work(path: String) =
    createIdentifiedWorkWith(
      collection = Some(Collection(path = path)),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  def storeWorks(index: Index, works: List[IdentifiedWork] = works) =
    insertIntoElasticsearch(index, works: _*)

  val workA = work("a")
  val workB = work("a/b")
  val workC = work("a/b/c")
  val workD = work("a/b/d")
  val workE = work("a/e")
  val workF = work("a/e/f")
  val workG = work("a/e/f/g")
  val workX = work("x")
  val workY = work("x/y")
  val workZ = work("x/oops/z")

  val works =
    List(workA, workB, workC, workD, workE, workF, workG, workX, workY, workZ)

  it("Retrieves a tree with the given path and all ancestors expanded") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("a/b"))) { result =>
        result shouldBe Right(
          CollectionTree(
            path = "a",
            work = workA,
            children = List(
              CollectionTree(
                path = "a/b",
                work = workB,
                children = List(
                  CollectionTree(path = "a/b/c", work = workC),
                  CollectionTree(path = "a/b/d", work = workD),
                )
              ),
              CollectionTree(path = "a/e", work = workE),
            )
          )
        )
      }
    }
  }

  it("Retrieves a tree with multiple paths and their ancestors expanded") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("a/b", "a/e/f"))) { result =>
        result shouldBe Right(
          CollectionTree(
            path = "a",
            work = workA,
            children = List(
              CollectionTree(
                path = "a/b",
                work = workB,
                children = List(
                  CollectionTree(path = "a/b/c", work = workC),
                  CollectionTree(path = "a/b/d", work = workD),
                )
              ),
              CollectionTree(
                path = "a/e",
                work = workE,
                children = List(
                  CollectionTree(
                    path = "a/e/f",
                    work = workF,
                    children = List(
                      CollectionTree(path = "a/e/f/g", work = workG)
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
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("a"))) { result =>
        result shouldBe Right(
          CollectionTree(
            path = "a",
            work = workA,
            children = List(
              CollectionTree(path = "a/b", work = workB),
              CollectionTree(path = "a/e", work = workE),
            )
          )
        )
      }
    }
  }

  it("Fails creating a tree when incomplete data") {
    withLocalWorksIndex { index =>
      storeWorks(index)
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("x/oops"))) { result =>
        result shouldBe a[Left[_, _]]
        result.left.get.getMessage shouldBe "Not all works in collection are connected to root 'x': x/oops/z"
      }
    }
  }

  it("Fails creating a tree when duplicate paths") {
    withLocalWorksIndex { index =>
      storeWorks(index, work("a/e/f/g") :: works)
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("a/e/f"))) { result =>
        result shouldBe a[Left[_, _]]
        result.left.get.getMessage shouldBe "Tree contains duplicate paths: a/e/f/g"
      }
    }
  }

  it("Excludes larger fields from works stored in the tree") {
    withLocalWorksIndex { index =>
      val p = work("p") withData (_.copy(items = List(createIdentifiedItem)))
      val q = work("p/q") withData (_.copy(notes = List(GeneralNote("hi"))))
      storeWorks(index, List(p, q))
      val service = collectionService(index)
      whenReady(service.retrieveTree(List("p/q"))) { result =>
        result shouldBe Right(
          CollectionTree(
            path = "p",
            work = p.withData(_.copy(items = Nil)),
            children = List(
              CollectionTree(
                path = "p/q",
                work = q.withData(_.copy(notes = Nil)),
              )
            )
          )
        )
      }
    }
  }
}
