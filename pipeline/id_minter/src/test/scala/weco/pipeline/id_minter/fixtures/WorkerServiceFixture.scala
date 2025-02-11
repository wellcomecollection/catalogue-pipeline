package weco.pipeline.id_minter.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.duration._
import io.circe.Json
import io.circe.syntax._

import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.{Identified, Source}
import weco.catalogue.internal_model.work.Work
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.services.IdMinterWorkerService
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

trait WorkerServiceFixture
    extends IdentifiersDatabase
    with PipelineStorageStreamFixtures {
  def withWorkerService[R](
    messageSender: MemoryMessageSender = new MemoryMessageSender(),
    queue: Queue = Queue("url://q", "arn::q", visibilityTimeout = 1 seconds),
    identifiersTableConfig: IdentifiersTableConfig,
    mergedIndex: Map[String, Json] = Map.empty,
    identifiedIndex: mutable.Map[String, Work[Identified]] = mutable.Map.empty
  )(testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withPipelineStream(
      queue,
      new MemoryIndexer(index = identifiedIndex),
      messageSender
    ) {
      stream =>
        val workerService = new IdMinterWorkerService(
          maybeIdentifierGenerator = None,
          pipelineStream = stream,
          jsonRetriever =
            new MemoryRetriever(index = mutable.Map(mergedIndex.toSeq: _*)),
          rdsClientConfig = rdsClientConfig,
          identifiersTableConfig = identifiersTableConfig
        )

        workerService.run()

        testWith(workerService)
    }
//
//  def withWorkerService[R](
//    messageSender: MemoryMessageSender,
//    queue: Queue,
//    identifiersTableConfig: IdentifiersTableConfig,
//    mergedIndex: Map[String, Json],
//    identifiedIndex: mutable.Map[String, Work[Identified]]
//  )(testWith: TestWith[IdMinterWorkerService[String], R]): R = {
//    Class.forName("com.mysql.cj.jdbc.Driver")
//    ConnectionPool.singleton(
//      s"jdbc:mysql://$rdsHost:$port",
//      rdsUsername,
//      rdsPassword,
//      settings = ConnectionPoolSettings(maxSize = rdsMaxPoolSize)
//    )
//
//    withWorkerService(
//      messageSender,
//      queue,
//      identifiersTableConfig,
//      mergedIndex,
//      identifiedIndex
//    ) {
//      service =>
//        testWith(service)
//    }
//  }

  def createIndex(works: List[Work[Source]]): Map[String, Json] =
    works.map(work => (work.id, work.asJson)).toMap
}
