package weco.pipeline.id_minter.services

import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Flow
import grizzled.slf4j.Logging
import io.circe.Json
import software.amazon.awssdk.services.sqs.model.Message
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline_storage.PipelineStorageStream.{
  batchRetrieveFlow,
  processFlow
}
import weco.typesafe.Runnable
import weco.catalogue.internal_model.work.Work
import weco.pipeline.id_minter.IdMinter
import weco.pipeline.id_minter.config.builders.RDSBuilder
import weco.pipeline.id_minter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import weco.pipeline.id_minter.database.{
  RDSIdentifierGenerator,
  TableProvisioner
}
import weco.pipeline.id_minter.steps.IdentifierGenerator
import weco.pipeline_storage.{PipelineStorageStream, Retriever}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class IdMinterWorkerService[Destination](
  maybeIdentifierGenerator: Option[IdentifierGenerator],
  pipelineStream: PipelineStorageStream[NotificationMessage, Work[
    Identified
  ], Destination],
  jsonRetriever: Retriever[Json],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {
  private val identifierGenerator = maybeIdentifierGenerator.getOrElse(
    RDSIdentifierGenerator(
      rdsClientConfig,
      identifiersTableConfig
    )
  )
  private val minter = new IdMinter(identifierGenerator)
  def run(): Future[Done] = {
    RDSBuilder.buildDB(rdsClientConfig)

    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    pipelineStream.run(
      this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, jsonRetriever))
        .via(
          processFlow(
            pipelineStream.config,
            item => Future.fromTry(processMessage(item))
          )
        )
    )
  }

  def processMessage(json: Json): Try[List[Work[Identified]]] =
    minter.processJson(json) match {
      case Failure(exception) => Failure(exception)
      case Success(value)     => Success(List(value))
    }
}
