package weco.pipeline.id_minter.services

import akka.Done
import akka.stream.scaladsl.Flow
import grizzled.slf4j.Logging
import io.circe.{Decoder, Json}
import software.amazon.awssdk.services.sqs.model.Message
import weco.json.JsonUtil
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline_storage.PipelineStorageStream.{
  batchRetrieveFlow,
  processFlow
}
import weco.typesafe.Runnable
import weco.catalogue.internal_model.work.Work
import weco.pipeline.id_minter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import weco.pipeline.id_minter.database.TableProvisioner
import weco.pipeline.id_minter.steps.{
  IdentifierGenerator,
  SourceIdentifierEmbedder
}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IdMinterWorkerService[Destination](
  identifierGenerator: IdentifierGenerator,
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Identified],
                                        Destination],
  jsonRetriever: Retriever[Json],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  def run(): Future[Done] = {
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
        .via(processFlow(pipelineStream.config, item => processMessage(item)))
    )
  }

  def processMessage(json: Json): Future[List[Work[Identified]]] =
    for {
      updatedJson <- Future.fromTry(embedIds(json))
      work <- Future.fromTry(decodeJson(updatedJson))
    } yield List(work)

  def embedIds(json: Json): Try[Json] =
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers)
      updatedJson <- SourceIdentifierEmbedder.update(json, mintedIdentifiers)
    } yield updatedJson

  def decodeJson(json: Json)(
    implicit decoder: Decoder[Work[Identified]]): Try[Work[Identified]] =
    JsonUtil.fromJson[Work[Identified]](json.noSpaces)(decoder)
}
