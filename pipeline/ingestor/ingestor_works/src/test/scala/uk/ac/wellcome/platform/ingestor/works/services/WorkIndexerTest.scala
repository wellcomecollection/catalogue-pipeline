package uk.ac.wellcome.platform.ingestor.works.services

import scala.concurrent.ExecutionContext.Implicits.global
import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

class WorkIndexerTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with WorkGenerators {

  describe("updating merged / redirected works") {
    it(
      "doesn't override a merged Work with same version but hasMultipleSources = false") {
      val mergedWork = identifiedWork(hasMultipleSources = true).withVersion(3)

      val unmergedWork = identifiedWork(
        sourceIdentifier = mergedWork.sourceIdentifier,
        hasMultipleSources = false
      ).withVersion(mergedWork.version)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val unmergedWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = mergedWork,
            secondWork = unmergedWork,
            index = index
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
      val unmergedNewWork = identifiedWork()
      val mergedOldWork = identifiedWork(
        sourceIdentifier = unmergedNewWork.sourceIdentifier,
        hasMultipleSources = true
      ).withVersion(unmergedNewWork.version - 1)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val mergedWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = unmergedNewWork,
            secondWork = mergedOldWork,
            index = index
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
          val redirectedWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = identifiedNewWork,
            secondWork = redirectedOldWork,
            index = index
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
      val redirectedWork = identifiedWork(hasMultipleSources = false)
        .redirected(
          IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ))

      val identifiedOldWork =
        identifiedWork(
          hasMultipleSources = false,
          canonicalId = redirectedWork.state.canonicalId)
          .withVersion(redirectedWork.version)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val identifiedWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = redirectedWork,
            secondWork = identifiedOldWork,
            index = index
          )
          whenReady(identifiedWorkInsertFuture) { result =>
            assertIngestedWorkIs(
              result = result,
              ingestedWork = redirectedWork,
              index = index)
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
          val invisibleWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = work,
            secondWork = invisibleWork,
            index = index
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

  private def ingestWorkPairInOrder(
    workIndexer: ElasticIndexer[Work[Identified]])(firstWork: Work[Identified],
                                                   secondWork: Work[Identified],
                                                   index: Index) =
    for {
      _ <- workIndexer.index(documents = List(firstWork))
      result <- workIndexer.index(documents = List(secondWork))
    } yield result

  private def assertIngestedWorkIs(
    result: Either[Seq[Work[Identified]], Seq[Work[Identified]]],
    ingestedWork: Work[Identified],
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasWork(index = index, ingestedWork)
  }

  def withWorksIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[Work[Identified]]), R]): R = {
    withLocalWorksIndex { index =>
      val indexer = new ElasticIndexer(elasticClient, index)
      testWith((index, indexer))
    }
  }
}
