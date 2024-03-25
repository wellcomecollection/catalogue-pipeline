package weco.pipeline_storage

import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.{RandomGenerators, TestWith}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait IndexerTestCases[Context, Document]
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with RandomGenerators {

  def withContext[R](documents: Seq[Document] = Seq.empty)(
    testWith: TestWith[Context, R]
  ): R

  def withIndexer[R](testWith: TestWith[Indexer[Document], R])(
    implicit context: Context
  ): R

  def createDocumentWith(
    id: String = randomAlphanumeric(),
    version: Int = randomInt(1, 5)
  ): Document

  def createDocument: Document =
    createDocumentWith()

  def assertIsIndexed(doc: Document)(implicit context: Context): Assertion
  def assertIsNotIndexed(doc: Document)(implicit context: Context): Assertion

  describe("behaves as an Indexer") {
    it("indexes a single document") {
      val doc = createDocument

      withContext() {
        implicit context =>
          withIndexer {
            indexer =>
              val future = indexer(doc)

              whenReady(future) {
                result =>
                  result.right.get should contain(doc)
                  assertIsIndexed(doc)
              }
          }
      }
    }

    it("stores the same document multiple times") {
      val doc = createDocument

      withContext() {
        implicit context =>
          withIndexer {
            indexer =>
              val futures = Future.sequence(
                (1 to 3).map {
                  _ =>
                    indexer(doc)
                }
              )

              whenReady(futures) {
                _ =>
                  assertIsIndexed(doc)
              }
          }
      }
    }

    it("doesn't replace a document with an older version") {
      val id = randomAlphanumeric()

      val doc1 = createDocumentWith(id = id, version = 1)
      val doc2 = createDocumentWith(id = id, version = 2)

      withContext(documents = Seq(doc2)) {
        implicit context =>
          withIndexer {
            indexer =>
              val future = indexer(doc1)

              whenReady(future) {
                result =>
                  result.isRight shouldBe true
                  assertIsIndexed(doc2)
                  assertIsNotIndexed(doc1)
              }
          }
      }
    }

    it("replaces a document with a newer version") {
      val id = randomAlphanumeric()

      val doc1 = createDocumentWith(id = id, version = 1)
      val doc2 = createDocumentWith(id = id, version = 2)

      withContext(documents = Seq(doc1)) {
        implicit context =>
          withIndexer {
            indexer =>
              val future = indexer(doc2)

              whenReady(future) {
                result =>
                  result.isRight shouldBe true
                  assertIsIndexed(doc2)
                  assertIsNotIndexed(doc1)
              }
          }
      }
    }

    it("replaces a document with the same version") {
      val id = randomAlphanumeric()

      val doc1a = createDocumentWith(id = id, version = 1)
      val doc1b = createDocumentWith(id = id, version = 1)

      withContext(documents = Seq(doc1a)) {
        implicit context =>
          withIndexer {
            indexer =>
              val future = indexer(doc1b)

              whenReady(future) {
                result =>
                  result.isRight shouldBe true
                  assertIsIndexed(doc1b)
                  assertIsNotIndexed(doc1a)
              }
          }
      }
    }

    it("indexes a list of documents") {
      val documents = (1 to 5).map {
        _ =>
          createDocument
      }

      withContext() {
        implicit context =>
          withIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                result =>
                  result.right.get should contain theSameElementsAs documents
                  documents.foreach {
                    doc =>
                      assertIsIndexed(doc)
                  }
              }
          }
      }
    }
  }
}
