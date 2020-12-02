package uk.ac.wellcome.pipeline_storage

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.fixtures.TestWith

trait RetrieverTestCases[Context, T]
    extends AnyFunSpec
    with Matchers
    with ScalaFutures {
  def withContext[R](documents: Seq[T])(testWith: TestWith[Context, R]): R

  def withRetriever[R](testWith: TestWith[Retriever[T], R])(
    implicit context: Context): R

  def createT: T

  implicit val id: CanonicalId[T]

  it("retrieves a document") {
    val t = createT

    withContext(documents = Seq(t)) { implicit context =>
      val future = withRetriever { _.apply(id.canonicalId(t)) }

      whenReady(future) {
        _ shouldBe t
      }
    }
  }

  it("throws an error if asked to retrieve a missing document") {
    val missingId = id.canonicalId(createT)
    val someOtherDocument = createT

    withContext(documents = Seq(someOtherDocument)) { implicit context =>
      val future = withRetriever { _.apply(missingId) }

      whenReady(future.failed) { exc =>
        exc shouldBe a[RetrieverNotFoundException]
        exc.getMessage shouldBe s"Nothing found with ID $missingId!"
      }
    }
  }

  it("retrieves multiple documents") {
    val t1 = createT
    val t2 = createT
    val t3 = createT

    val documents = Seq(t1, t2, t3)
    val ids = documents.map { id.canonicalId }
    val expectedResult = RetrieverMultiResult(
      found =
        documents
          .map { t => id.canonicalId(t) -> t }
          .toMap,
      notFound = Map.empty
    )

    withContext(documents = documents) { implicit context =>
      val future = withRetriever { _.apply(ids) }

      whenReady(future) {
        _ shouldBe expectedResult
      }
    }
  }

  it("fails if asked to find multiple documents, and one of them is missing") {
    val t1 = createT
    val t2 = createT
    val t3 = createT

    val documents = Seq(t1, t2, t3)
    val ids = documents.map { id.canonicalId }

    withContext(documents = Seq(t1, t2)) { implicit context =>
      val future = withRetriever { _.apply(ids) }

      whenReady(future) { result =>
        result.found shouldBe Map(
          id.canonicalId(t1) -> t1, id.canonicalId(t2) -> t2
        )

        result.notFound.keySet shouldBe Set(id.canonicalId(t3))
        result.notFound(id.canonicalId(t3)) shouldBe a[RetrieverNotFoundException]
      }
    }
  }
}
