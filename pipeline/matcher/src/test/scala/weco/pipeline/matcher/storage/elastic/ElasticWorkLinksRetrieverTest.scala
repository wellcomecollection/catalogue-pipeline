package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.Index
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.pipeline.matcher.generators.WorkLinksGenerators
import weco.pipeline.matcher.models.WorkLinks
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures
import weco.pipeline_storage.{Retriever, RetrieverTestCases}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticWorkLinksRetrieverTest
    extends RetrieverTestCases[Index, WorkLinks]
    with ElasticIndexerFixtures
    with WorkGenerators
    with WorkLinksGenerators {

  it("can retrieve a deleted work") {
    val work: Work[WorkState.Identified] = identifiedWork().deleted()
    withLocalElasticsearchIndex(config = WorksIndexConfig.identified) {
      implicit index =>
        withElasticIndexer(index) {
          indexer: ElasticIndexer[Work[WorkState.Identified]] =>
            whenReady(indexer(Seq(work))) { _ =>
              implicit val id: IndexId[Work[WorkState.Identified]] =
                (w: Work[WorkState.Identified]) => w.id
              assertElasticsearchEventuallyHas(index, work)

              withRetriever { retriever: Retriever[WorkLinks] =>
                whenReady(retriever(id.indexId(work))) { result =>
                  result shouldBe WorkLinks(work)
                }
              }
            }
        }
    }

  }

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
                  reason = "Linked in the matcher tests"
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
