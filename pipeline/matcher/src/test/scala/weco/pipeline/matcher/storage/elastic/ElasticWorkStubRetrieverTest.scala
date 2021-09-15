package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.Index
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures
import weco.pipeline_storage.{Retriever, RetrieverTestCases}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticWorkStubRetrieverTest
    extends RetrieverTestCases[Index, WorkStub]
    with ElasticIndexerFixtures
    with WorkGenerators
    with WorkStubGenerators {

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

              withRetriever { retriever: Retriever[WorkStub] =>
                whenReady(retriever(id.indexId(work))) { result =>
                  result shouldBe WorkStub(work)
                }
              }
            }
        }
    }

  }

  override def withContext[R](links: Seq[WorkStub])(
    testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = WorksIndexConfig.identified) { index =>
      withElasticIndexer[Work[WorkState.Identified], R](index) { indexer =>
        val works: Seq[Work[WorkState.Identified]] = links.map { lk =>
          identifiedWork(canonicalId = lk.id)
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

  override def withRetriever[R](testWith: TestWith[Retriever[WorkStub], R])(
    implicit index: Index): R =
    testWith(
      new ElasticWorkStubRetriever(elasticClient, index)
    )

  override def createT: WorkStub = createWorkStub

  override implicit val id: IndexId[WorkStub] =
    (work: WorkStub) => work.id.underlying
}
