package weco.pipeline.ingestor.works.fixtures

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{Assertion, Suite}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.fixtures.{TestWith, TimeAssertions}
import weco.messaging.fixtures.SQS.Queue
import weco.pipeline.ingestor.fixtures.IngestorFixtures
import weco.pipeline.ingestor.works.WorkTransformer
import weco.pipeline.ingestor.works.models.{
  IndexedWork,
  WorkAggregatableValues,
  WorkFilterableValues
}
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.json.JsonUtil._
import weco.pipeline.ingestor.common.models.WorkQueryableValues

trait WorksIngestorFixtures
    extends IndexFixtures
    with IngestorFixtures
    with TimeAssertions {
  this: Suite =>

  def assertWorkIndexed(
    index: Index,
    work: Work[WorkState.Denormalised]
  ): Assertion = {
    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, work.state.canonicalId.toString)
      }.await

      val getResponse = response.result

      if (!getResponse.exists) {
        println(s"getResponse = $getResponse")
      }

      getResponse.exists shouldBe true

      val storedWork =
        fromJson[IndexedWork](getResponse.sourceAsString).get
      val expectedWork = WorkTransformer.deriveData(work)

      (storedWork, work) match {
        case (
              IndexedWork.Visible(
                _,
                _,
                storedQuery,
                storedAggregations,
                storedFilters
              ),
              visibleWork: Work.Visible[WorkState.Denormalised]
            ) =>
          storedQuery shouldBe WorkQueryableValues(visibleWork)
          storedAggregations shouldBe WorkAggregatableValues(visibleWork)
          storedFilters shouldBe WorkFilterableValues(visibleWork)
        case _ => ()
      }

      storedWork.debug.source shouldBe expectedWork.debug.source

      assertRecent(storedWork.debug.indexedTime)
    }
  }

  def withWorksIngestor[R](
    queue: Queue,
    existingWorks: Seq[Work[WorkState.Denormalised]]
  )(testWith: TestWith[Index, R]): R =
    withLocalWorksIndex {
      index =>
        withLocalDenormalisedWorksIndex {
          denormalisedIndex =>
            insertIntoElasticsearch(denormalisedIndex, existingWorks: _*)

            val retriever = new ElasticSourceRetriever[Work[Denormalised]](
              client = elasticClient,
              index = denormalisedIndex
            )

            val indexer = new ElasticIndexer[IndexedWork](
              client = elasticClient,
              index = index
            )

            withWorkerService(
              queue,
              retriever,
              indexer,
              transform = WorkTransformer.deriveData
            ) {
              service =>
                testWith(index)
            }
        }
    }
}
