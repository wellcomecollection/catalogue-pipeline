package uk.ac.wellcome.platform.ingestor.works

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{Assertion, Suite}
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.Queue
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.pipeline_storage.Retriever
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.pipeline_storage.{Indexer, Retriever}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures

import java.time.{Duration, Instant}

trait IngestorFixtures
    extends IndexFixtures
    with PipelineStorageStreamFixtures {
  this: Suite =>

  def assertRecent(instant: Instant, recentSeconds: Int = 1): Assertion =
    Duration
      .between(instant, Instant.now)
      .getSeconds should be <= recentSeconds.toLong

  def assertWorkIndexed(index: Index,
                        work: Work[WorkState.Denormalised]): Assertion =
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

  def withWorkerService[R](queue: Queue,
                           retriever: Retriever[Work[Denormalised]],
                           indexer: Indexer[Work[Indexed]])(
    testWith: TestWith[WorkIngestorWorkerService[String], R]): R = {
    withPipelineStream(
      queue,
      indexer,
      pipelineStorageConfig = pipelineStorageConfig) { pipelineStream =>
      val workerService = new WorkIngestorWorkerService(
        pipelineStream = pipelineStream,
        workRetriever = retriever
      )

      workerService.run()

      testWith(workerService)
    }
  }
}
