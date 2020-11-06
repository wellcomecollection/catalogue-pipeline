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

  it(
    "throws a RetrieverNotFoundException if asked to retrieve a missing document") {
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
}
