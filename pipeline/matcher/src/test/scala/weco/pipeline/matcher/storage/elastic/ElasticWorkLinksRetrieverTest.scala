package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.Index
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.pipeline_storage.Retriever
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}
import weco.pipeline.matcher.generators.WorkLinksGenerators
import weco.pipeline.matcher.models.WorkLinks
import weco.pipeline_storage.{Retriever, RetrieverTestCases}
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticWorkLinksRetrieverTest
    extends RetrieverTestCases[Index, WorkLinks]
    with ElasticIndexerFixtures
    with WorkGenerators
    with WorkLinksGenerators {

  override def withContext[R](links: Seq[WorkLinks])(
    testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = WorksIndexConfig.identified) { index =>
      withElasticIndexer[Work[WorkState.Identified], R](index) { indexer =>
        val works: Seq[Work[WorkState.Identified]] = links.map { lk =>
          identifiedWork(canonicalId = lk.workId)
            .withVersion(lk.version)
            .mergeCandidates(
              lk.referencedWorkIds.map { id =>
                MergeCandidate(
                  id = IdState.Identified(
                    canonicalId = id,
                    sourceIdentifier = createSourceIdentifier
                  ),
                  reason = None
                )
              }.toList
            )
        }

        whenReady(indexer(works)) { _ =>
          implicit val id: IndexId[Work[WorkState.Identified]] =
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

  override implicit val id: IndexId[WorkLinks] =
    (links: WorkLinks) => links.workId.underlying
}
