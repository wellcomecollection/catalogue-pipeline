package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
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
    it("doesn't override a merged Work with the same version but only 1 source") {
      val mergedWork = identifiedWork(numberOfSources = 2).withVersion(3)

      val unmergedWork = identifiedWork(
        sourceIdentifier = mergedWork.sourceIdentifier,
        numberOfSources = 1
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

    it("doesn't overwrite a Work with lower version and multiple sources") {
      val unmergedNewWork = identifiedWork(numberOfSources = 1).withVersion(4)
      val mergedOldWork = identifiedWork(
        sourceIdentifier = unmergedNewWork.sourceIdentifier,
        numberOfSources = 2
      ).withVersion(unmergedNewWork.version - 1)

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
      "doesn't override an identified Work with a redirected work with lower version") {
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

    it(
      "doesn't override a redirected Work with an identified work with the same version") {
      val redirectedWork = identifiedWork(numberOfSources = 1)
        .redirected(
          IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ))

      val identifiedOldWork =
        identifiedWork(
          canonicalId = redirectedWork.state.canonicalId,
          numberOfSources = 1)
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
      val mergedWork2 = identifiedWork(numberOfSources = 2)
      val mergedWork3 =
        mergedWork2.copy(state = mergedWork2.state.copy(numberOfSources = 3))
      val mergedWork4 =
        mergedWork2.copy(state = mergedWork2.state.copy(numberOfSources = 4))

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            mergedWork2,
            mergedWork4,
            mergedWork3
          )
          whenReady(insertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = mergedWork4,
              index = index
            )
          }
      }
    }

    it(
      "overrides an identified Work with an invisible work with a higher version") {
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

    it("throws an error if a work has >= versionMultiplier sources") {
      val erroneousWork =
        identifiedWork(numberOfSources = Indexable.versionMultiplier)

      withWorksIndexAndIndexer {
        case (_, indexer) =>
          a[RuntimeException] should be thrownBy {
            ingestInOrder(indexer)(erroneousWork)
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
