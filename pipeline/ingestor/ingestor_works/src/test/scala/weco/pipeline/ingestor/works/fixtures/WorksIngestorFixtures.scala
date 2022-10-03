package weco.pipeline.ingestor.works.fixtures

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{Assertion, Suite}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.{IndexFixtures, WorksIndexConfig}
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.fixtures.{TestWith, TimeAssertions}
import weco.messaging.fixtures.SQS.Queue
import weco.pipeline.ingestor.fixtures.IngestorFixtures
import weco.pipeline.ingestor.works.WorkTransformer
import weco.pipeline.ingestor.works.models.{IndexedWork, WorkAggregatableValues}
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
  ): Assertion =
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

      storedWork match {
        case w @ IndexedWork.Visible(
              _,
              _,
              storedQuery,
              storedAggregations) =>
          storedQuery shouldBe WorkQueryableValues(
            id = work.state.canonicalId,
            sourceIdentifier = work.state.sourceIdentifier,
            workData = work.data,
            relations = work.state.relations,
            availabilities = work.state.availabilities
          )
          storedAggregations shouldBe WorkAggregatableValues(
            work.data,
            work.state.availabilities)
        case _ => ()
      }

      storedWork.debug.source shouldBe expectedWork.debug.source

      assertRecent(storedWork.debug.indexedTime)
    }

  def withWorksIngestor[R](queue: Queue,
                           existingWorks: Seq[Work[WorkState.Denormalised]])(
    testWith: TestWith[Index, R]): R =
    withLocalWorksIndex { index =>
      withLocalDenormalisedWorksIndex { denormalisedIndex =>
        insertIntoElasticsearch(denormalisedIndex, existingWorks: _*)

        val retriever = new ElasticSourceRetriever[Work[Denormalised]](
          client = elasticClient,
          index = denormalisedIndex
        )

        val indexer = new ElasticIndexer[IndexedWork](
          client = elasticClient,
          index = index,
          config = WorksIndexConfig.indexed
        )

        withWorkerService(
          queue,
          retriever,
          indexer,
          transform = WorkTransformer.deriveData) { service =>
          testWith(index)
        }
      }
    }
}
