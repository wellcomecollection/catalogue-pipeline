package uk.ac.wellcome.platform.id_minter.services

import akka.Done
import grizzled.slf4j.Logging
import io.circe.{Decoder, Json}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.id_minter.config.models.{IdentifiersTableConfig, RDSClientConfig}
import uk.ac.wellcome.platform.id_minter.database.TableProvisioner
import uk.ac.wellcome.platform.id_minter.steps.{IdentifierGenerator, SourceIdentifierEmbedder}
import uk.ac.wellcome.typesafe.Runnable

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

    pipelineStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  def processMessage(
    message: NotificationMessage): Future[Option[Work[Identified]]] =
    jsonRetriever(message.body)
      .flatMap(json => Future.fromTry(embedIds(json)))
      .flatMap(updatedJson =>
        Future.fromTry(decodeJson(updatedJson)).map(Some(_)))

  def embedIds(json: Json): Try[Json] =
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers)
      updatedJson <- SourceIdentifierEmbedder.update(json, mintedIdentifiers)
    } yield updatedJson

  def decodeJson(json: Json)(
    implicit decoder: Decoder[Work[Identified]]): Try[Work[Identified]] =
    decoder.decodeJson(json).toTry
}
