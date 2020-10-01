package uk.ac.wellcome.platform.ingestor.works.services

import scala.concurrent.ExecutionContext.Implicits.global
import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.LegacyWorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

class WorkIndexerTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with ElasticsearchFixtures
    with LegacyWorkGenerators {

  describe("updating merged / redirected works") {
    it(
      "doesn't override a merged Work with same version but merged flag = false") {
      val mergedWork = createIdentifiedWorkWith(version = 3, merged = true)
      val unmergedWork = mergedWork.copy(
        state = mergedWork.state.copy(hasMultipleSources = false)
      )

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

    it("doesn't overwrite a Work with lower version and merged = true") {
      val unmergedNewWork = createIdentifiedWorkWith(version = 4)
      val mergedOldWork = unmergedNewWork
        .copy(
          version = 3,
          state = unmergedNewWork.state.copy(hasMultipleSources = true)
        )

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
      val identifiedNewWork = createIdentifiedWorkWith(version = 4)
      val redirectedOldWork = createIdentifiedRedirectedWorkWith(
        canonicalId = identifiedNewWork.state.canonicalId,
        version = 3)

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
      val redirectedWork = createIdentifiedRedirectedWorkWith(version = 3)
      val identifiedWork = createIdentifiedWorkWith(
        canonicalId = redirectedWork.state.canonicalId,
        version = 3)

      withWorksIndexAndIndexer {
        case (index, indexer) =>
          val identifiedWorkInsertFuture = ingestWorkPairInOrder(indexer)(
            firstWork = redirectedWork,
            secondWork = identifiedWork,
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
      val work = createIdentifiedWorkWith(version = 3)
      val invisibleWork = createIdentifiedInvisibleWorkWith(
        canonicalId = work.state.canonicalId,
        version = 4)

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
    testWith: TestWith[(Index, ElasticIndexer[Work[Identified]]), R]) = {
    withLocalWorksIndex { index =>
      val indexer = new ElasticIndexer(elasticClient, index)
      testWith((index, indexer))
    }
  }
}
