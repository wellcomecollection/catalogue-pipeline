package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class WorkIndexableTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with ElasticsearchFixtures
    with ElasticIndexerFixtures
    with WorkGenerators {

  describe("updating merged / redirected works") {
    it(
      "doesn't override a merged Work with same version but hasMultipleSources = false") {
      val mergedWork = identifiedWork(nMergedSources = 1).withVersion(3)

      val unmergedWork = identifiedWork(
        sourceIdentifier = mergedWork.sourceIdentifier,
        nMergedSources = 0
      ).withVersion(mergedWork.version)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = ingestInOrder(indexer)(
            mergedWork,
            unmergedWork
          )

          whenReady(unmergedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = mergedWork,
              index = index)
          }
      }
    }

    it(
      "doesn't overwrite a Work with lower version and hasMultipleSources = true") {
      val unmergedNewWork = identifiedWork.
      val mergedOldWork = identifiedWork(
        sourceIdentifier = unmergedNewWork.sourceIdentifier,
        hasMultipleSources = true
      ).withVersion(unmergedNewWork.version - 1)
    it("doesn't overwrite a Work with lower version and multiple sources") {
      val unmergedNewWork = createIdentifiedWorkWith(version = 4)
      val mergedOldWork = unmergedNewWork
        .copy(
          version = 3,
          state = unmergedNewWork.state.copy(nMergedSources = 1)
        )

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val mergedWorkInsertFuture = ingestInOrder(indexer)(
            unmergedNewWork,
            mergedOldWork
          )
          whenReady(mergedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = unmergedNewWork,
              index = index)
          }
      }
    }

    it(
      "doesn't override a identified Work with redirected work with lower version") {
      val identifiedNewWork = identifiedWork()

      val redirectedOldWork =
        identifiedWork(canonicalId = identifiedNewWork.state.canonicalId)
          .withVersion(identifiedNewWork.version - 1)
          .redirected(
            IdState.Identified(
              canonicalId = createCanonicalId,
              sourceIdentifier = createSourceIdentifier
            )
          )

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val redirectedWorkInsertFuture = ingestInOrder(indexer)(
            identifiedNewWork,
            redirectedOldWork
          )
          whenReady(redirectedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = identifiedNewWork,
              index = index)
          }
      }
    }

    it("doesn't override a redirected Work with identified work same version") {
      val redirectedWork = identifiedWork()
        .redirected(
          IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ))

      val identifiedOldWork =
        identifiedWork(canonicalId = redirectedWork.state.canonicalId)
          .withVersion(redirectedWork.version)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val identifiedWorkInsertFuture = ingestInOrder(indexer)(
            redirectedWork,
            identifiedOldWork
          )
          whenReady(identifiedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = redirectedWork,
              index = index)
          }
      }
    }

    it("overrides a merged work with one that has been merged again") {
      val mergedWork1 = createIdentifiedWorkWith(nMergedSources = 1)
      val mergedWork2 = createIdentifiedWorkWith(nMergedSources = 2)
      val mergedWork3 = createIdentifiedWorkWith(nMergedSources = 3)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            mergedWork1,
            mergedWork3,
            mergedWork2
          )
          whenReady(insertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = mergedWork3,
              index = index
            )
          }
      }
    }

    it("overrides a identified Work with invisible work with higher version") {
      val work = identifiedWork()
      val invisibleWork =
        identifiedWork(canonicalId = work.state.canonicalId)
          .withVersion(work.version + 1)
          .invisible()

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val invisibleWorkInsertFuture = ingestInOrder(indexer)(
            work,
            invisibleWork
          )
          whenReady(invisibleWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = invisibleWork,
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
      val indexer = new ElasticIndexer(elasticClient, index)
      testWith((index, indexer))
    }
  }
}
