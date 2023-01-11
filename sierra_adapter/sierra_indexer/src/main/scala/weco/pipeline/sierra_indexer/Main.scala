package weco.pipeline.sierra_indexer

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.transfer.s3.S3TransferManager
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SQSBuilder
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.sierra_indexer.services.Worker

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()

    implicit val elasticClient: ElasticClient =
      ElasticBuilder.buildElasticClient(config)

    implicit val s3Client: S3Client = S3Client.builder().build()
    implicit val s3TransferManager: S3TransferManager =
      S3TransferManager.builder().build()

    new Worker(
      sqsStream = SQSBuilder.buildSQSStream(config),
      sierraReadable = S3TypedStore[SierraTransformable]
    )
  }
}
