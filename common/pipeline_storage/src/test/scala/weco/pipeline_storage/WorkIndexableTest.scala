package weco.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.TestWith
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.Implicits._
import Indexable.workIndexable
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class WorkIndexableTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with ElasticIndexerFixtures
    with WorkGenerators {

  describe("updating merged / redirected works") {
    it("overrides with a work with a later modifiedDate") {
      val originalWork = identifiedWork()

      val newWork: Work.Visible[Identified] = originalWork.copy(
        state = originalWork.state.copy(
          sourceModifiedTime = originalWork.state.sourceModifiedTime + (2 days)))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = indexInOrder(indexer)(
            originalWork,
            newWork
          )

          whenReady(unmergedWorkInsertFuture) {
            assertIndexedWorkIs(_, indexedWork = newWork, index = index)
          }
      }
    }

    it("overrides with a work with the same modifiedDate") {
      val originalWork = identifiedWork()

      val newWork: Work.Visible[Identified] = originalWork.copy(
        data = originalWork.data.copy(description = Some(randomAlphanumeric())))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = indexInOrder(indexer)(
            originalWork,
            newWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIndexedWorkIs(_, indexedWork = newWork, index = index)
          }
      }
    }
    it("does not override with a work with an earlier modifiedDate") {
      val originalWork = identifiedWork()

      val olderWork: Work.Visible[Identified] = originalWork.copy(
        state = originalWork.state.copy(
          sourceModifiedTime = originalWork.state.sourceModifiedTime - (2 days)))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = indexInOrder(indexer)(
            originalWork,
            olderWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIndexedWorkIs(_, indexedWork = originalWork, index = index)
          }
      }
    }

  }

  private def assertIndexedWorkIs(
    result: Either[Seq[Work[Identified]], Seq[Work[Identified]]],
    indexedWork: Work[Identified],
    index: Index): Seq[Assertion] = {
    result shouldBe a[Right[_, _]]
    assertElasticsearchEventuallyHasWork(index = index, indexedWork)
  }

  def withWorksIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[Work[Identified]]), R]): R = {
    withLocalIdentifiedWorksIndex { index =>
      val indexer =
        new ElasticIndexer[Work[Identified]](
          elasticClient,
          index)
      testWith((index, indexer))
    }
  }
}
