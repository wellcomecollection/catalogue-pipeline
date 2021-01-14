package uk.ac.wellcome.platform.matcher.storage.elastic

import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.IdState.Identified
import uk.ac.wellcome.models.work.internal.{MergeCandidate, Work, WorkState}
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures
import uk.ac.wellcome.pipeline_storage.{Retriever, RetrieverTestCases}
import uk.ac.wellcome.platform.matcher.generators.WorkLinksGenerators
import uk.ac.wellcome.platform.matcher.models.WorkLinks

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticWorkLinksRetrieverTest
    extends RetrieverTestCases[Index, WorkLinks]
    with ElasticsearchFixtures
    with ElasticIndexerFixtures
    with WorkGenerators
    with WorkLinksGenerators {

  override def withContext[R](links: Seq[WorkLinks])(
    testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = IdentifiedWorkIndexConfig) { index =>
      withElasticIndexer[Work[WorkState.Identified], R](index) { indexer =>
        val works: Seq[Work[WorkState.Identified]] = links.map { lk =>
          identifiedWork(canonicalId = lk.workId)
            .withVersion(lk.version)
            .mergeCandidates(
              lk.referencedWorkIds.map { id =>
                MergeCandidate(
                  id = Identified(
                    canonicalId = id,
                    sourceIdentifier = createSourceIdentifier
                  ),
                  reason = None
                )
              }.toList
            )
        }

        whenReady(indexer(works)) { _ =>
          implicit val id: CanonicalId[Work[WorkState.Identified]] =
            (w: Work[WorkState.Identified]) => w.id

          assertElasticsearchEventuallyHas(index, works: _*)

          testWith(index)
        }
      }
    }

  override def withRetriever[R](testWith: TestWith[Retriever[WorkLinks], R])(
    implicit index: Index): R =
    testWith(
      new ElasticWorkLinksRetriever(elasticClient, index)
    )

  override def createT: WorkLinks = createWorkLinks

  override implicit val id: CanonicalId[WorkLinks] =
    (links: WorkLinks) => links.workId
}
