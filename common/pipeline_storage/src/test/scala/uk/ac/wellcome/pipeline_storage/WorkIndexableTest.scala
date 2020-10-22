package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.{InstantGenerators, WorkGenerators}
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class WorkIndexableTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with ElasticsearchFixtures
    with ElasticIndexerFixtures
    with WorkGenerators with InstantGenerators{

  describe("updating merged / redirected works") {
    it("overrides with a work with a later modifiedDate") {
      val originalWork = identifiedWork()

      val newWork: Work.Visible[Identified] = originalWork.copy(state = originalWork.state.copy(modifiedTime = originalWork.state.modifiedTime + (2 days)))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = ingestInOrder(indexer)(
            originalWork,
            newWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = newWork,
              index = index)
          }
      }

    }

    it("overrides with a work with the same modifiedDate") {
      val originalWork = identifiedWork()

      val newWork: Work.Visible[Identified] = originalWork.copy(data= originalWork.data.copy(description = Some(randomAlphanumeric())))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = ingestInOrder(indexer)(
            originalWork,
            newWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = newWork,
              index = index)
          }
      }
    }
    it("does not override with a work with an earlier modifiedDate") {
      val originalWork = identifiedWork()

      val olderWork: Work.Visible[Identified] = originalWork.copy(state = originalWork.state.copy(modifiedTime = originalWork.state.modifiedTime - (2 days)))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = ingestInOrder(indexer)(
            originalWork,
            olderWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = originalWork,
              index = index)
          }
      }
    }

  }

  private def assertIngestedWorkIs(
    result: Either[Seq[Work[Identified]], Seq[Work[Identified]]],
    ingestedWork: Work[Identified],
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasWork(index = index, ingestedWork)
  }

  def withWorksIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[Work[Identified]]), R]) = {
    withLocalWorksIndex { index =>
      val indexer =
        new ElasticIndexer[Work[Identified]](
          elasticClient,
          index,
          IdentifiedWorkIndexConfig)
      testWith((index, indexer))
    }
  }
}
