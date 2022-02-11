package weco.pipeline.ingestor.works.fixtures

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{Assertion, Suite}
import weco.json.JsonUtil.fromJson
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.{IndexFixtures, WorksIndexConfig}
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.fixtures.{TestWith, TimeAssertions}
import weco.messaging.fixtures.SQS.Queue
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline.ingestor.fixtures.IngestorFixtures
import weco.pipeline.ingestor.works.WorkTransformer
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.Indexable.workIndexable

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

      getResponse.exists shouldBe true

      val storedWork =
        fromJson[Work[WorkState.Indexed]](getResponse.sourceAsString).get
      val expectedWork = WorkTransformer.deriveData(work)

      storedWork.data shouldBe expectedWork.data
      storedWork.version shouldBe expectedWork.version

      storedWork.state.sourceIdentifier shouldBe expectedWork.state.sourceIdentifier
      storedWork.state.canonicalId shouldBe expectedWork.state.canonicalId
      storedWork.state.mergedTime shouldBe expectedWork.state.mergedTime
      storedWork.state.sourceModifiedTime shouldBe expectedWork.state.sourceModifiedTime
      storedWork.state.availabilities shouldBe expectedWork.state.availabilities
      storedWork.state.derivedData shouldBe expectedWork.state.derivedData
      storedWork.state.relations shouldBe expectedWork.state.relations

      assertRecent(storedWork.state.indexedTime)
    }

  def withWorkIngestorWorkerService[R](queue: Queue,
                                       denormalisedIndex: Index,
                                       indexedIndex: Index)(
    testWith: TestWith[
      IngestorWorkerService[String, Work[Denormalised], Work[Indexed]],
      R]): R = {
    val retriever = new ElasticSourceRetriever[Work[Denormalised]](
      client = elasticClient,
      index = denormalisedIndex
    )

    val indexer = new ElasticIndexer[Work[Indexed]](
      client = elasticClient,
      index = indexedIndex,
      config = WorksIndexConfig.indexed
    )

    withWorkerService(
      queue,
      retriever,
      indexer,
      transform = WorkTransformer.deriveData) { service =>
      testWith(service)
    }
  }
}
