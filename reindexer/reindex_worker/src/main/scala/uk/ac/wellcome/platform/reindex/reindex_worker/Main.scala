package uk.ac.wellcome.platform.reindex.reindex_worker

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{NotificationStreamBuilder, SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.reindex.reindex_worker.config.ReindexJobConfigBuilder
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{MaxRecordsScanner, ParallelScanner, ScanSpecScanner}
import uk.ac.wellcome.platform.reindex.reindex_worker.models.ReindexRequest
import uk.ac.wellcome.platform.reindex.reindex_worker.services.{BulkSNSSender, RecordReader, ReindexWorkerService}
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val scanSpecScanner = new ScanSpecScanner(
      dynamoDBClient = DynamoBuilder.buildDynamoClient(config)
    )

    val recordReader = new RecordReader(
      maxRecordsScanner = new MaxRecordsScanner(
        scanSpecScanner = scanSpecScanner
      ),
      parallelScanner = new ParallelScanner(
        scanSpecScanner = scanSpecScanner
      )
    )

    val hybridRecordSender = new BulkSNSSender(
      snsMessageWriter = SNSBuilder.buildSNSMessageWriter(config)
    )

    new ReindexWorkerService(
      notificationStream = NotificationStreamBuilder.buildStream[ReindexRequest](config),
      recordReader = recordReader,
      bulkSNSSender = hybridRecordSender,
      reindexJobConfigMap =
        ReindexJobConfigBuilder.buildReindexJobConfigMap(config)
    )
  }
}
