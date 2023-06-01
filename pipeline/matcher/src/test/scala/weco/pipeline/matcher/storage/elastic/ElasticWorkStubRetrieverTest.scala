package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.Index
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
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
    withLocalIdentifiedWorksIndex {
      implicit index =>
        withElasticIndexer(index) {
          indexer: ElasticIndexer[Work[WorkState.Identified]] =>
            whenReady(indexer(Seq(work))) { _ =>
              assertElasticsearchEventuallyHas(index, work)

              withRetriever { retriever: Retriever[WorkStub] =>
                whenReady(retriever(indexId.indexId(work))) {
                  _ shouldBe WorkStub(
                    state = work.state,
                    version = work.version,
                    workType = "Deleted"
                  )
                }
              }
            }
        }
    }
  }

  override def withContext[R](stubs: Seq[WorkStub])(
    testWith: TestWith[Index, R]): R =
    withLocalIdentifiedWorksIndex { index =>
      withElasticIndexer[Work[WorkState.Identified], R](index) { indexer =>
        val works: Seq[Work[WorkState.Identified]] = stubs.map { w =>
          identifiedWork()
            .mapState { _ =>
              w.state
            }
            .withVersion(w.version)
        }

        whenReady(indexer(works)) { _ =>
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

  override def createT: WorkStub = createWorkWith(id = createCanonicalId)

  override implicit val id: IndexId[WorkStub] =
    (work: WorkStub) => work.id.underlying
}
