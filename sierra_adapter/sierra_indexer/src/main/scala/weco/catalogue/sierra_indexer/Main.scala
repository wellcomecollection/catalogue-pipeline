package weco.catalogue.sierra_indexer

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.sksamuel.elastic4s.ElasticClient
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.sierra_adapter.models.SierraTransformable
import weco.catalogue.sierra_adapter.models.Implicits._
import weco.catalogue.sierra_indexer.services.Worker

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()

    implicit val elasticClient: ElasticClient = ElasticBuilder.buildElasticClient(config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    new Worker(
      sqsStream = SQSBuilder.buildSQSStream(config),
      sierraReadable = S3TypedStore[SierraTransformable]
    )
  }
}
