package uk.ac.wellcome.platform.id_minter_works.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import akka.Done
import grizzled.slf4j.Logging
import io.circe.Json

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.platform.id_minter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.id_minter.database.TableProvisioner
import uk.ac.wellcome.platform.id_minter.steps.{
  IdentifierGenerator,
  SourceIdentifierEmbedder
}
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.pipeline_storage.Retriever
import uk.ac.wellcome.json.JsonUtil._

class IdMinterWorkerService[Destination](
  identifierGenerator: IdentifierGenerator,
  sender: MessageSender[Destination],
  messageStream: SQSStream[NotificationMessage],
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

    println("\n\nxxxxxxxxxxxxxxxxxxxxx\n\n\n")

    messageStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  def processMessage(message: NotificationMessage): Future[Unit] = {
    println(message.body)
    jsonRetriever(message.body)
      .flatMap(json => Future.fromTry(processJson(json)))
  }

  def processJson(json: Json): Try[Unit] =
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers)
      updatedJson <- SourceIdentifierEmbedder.update(json, mintedIdentifiers)
      _ <- sender.sendT(updatedJson)
    } yield ()
}
