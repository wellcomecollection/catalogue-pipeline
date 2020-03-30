package uk.ac.wellcome.platform.ingestor.works.services

import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.ingestor.common.Indexer
import uk.ac.wellcome.platform.ingestor.works.fixtures.IngestorWorksFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class WorkIndexerTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with IngestorWorksFixtures
    with WorksGenerators {

  describe("updating merged / redirected works") {
    it(
      "doesn't override a merged Work with same version but merged flag = false") {
      val mergedWork = createIdentifiedWorkWith(version = 3, merged = true)
      val unmergedWork = mergedWork.withData(_.copy(merged = false))

      withWorksIndexAndIndexer{ case (index, indexer) =>
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
        .copy(version = 3)
        .withData(_.copy(merged = true))

      withWorksIndexAndIndexer{ case (index, indexer) =>
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
        canonicalId = identifiedNewWork.canonicalId,
        version = 3)

      withWorksIndexAndIndexer{ case (index, indexer) =>
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
        canonicalId = redirectedWork.canonicalId,
        version = 3)

      withWorksIndexAndIndexer{ case (index, indexer) =>
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
        canonicalId = work.canonicalId,
        version = 4)

      withWorksIndexAndIndexer{ case (index, indexer) =>
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



  private def ingestWorkPairInOrder(workIndexer: Indexer[IdentifiedBaseWork])(firstWork: IdentifiedBaseWork,
                                    secondWork: IdentifiedBaseWork,
                                    index: Index) =
    for {
      _ <- workIndexer.index(documents = List(firstWork))
      result <- workIndexer.index(documents = List(secondWork))
    } yield result

  private def assertIngestedWorkIs(
    result: Either[Seq[IdentifiedBaseWork], Seq[IdentifiedBaseWork]],
    ingestedWork: IdentifiedBaseWork,
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasWork(index = index, ingestedWork)
  }

  def withWorksIndexAndIndexer[R](testWith: TestWith[(Index,WorkIndexer), R]) = {
    withLocalWorksIndex { index =>
      val indexer = new WorkIndexer(elasticClient, index)
        testWith((index, indexer))
    }
  }
}
