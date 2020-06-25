package uk.ac.wellcome.platform.idminter.services

import akka.Done
import grizzled.slf4j.Logging
import io.circe.Json
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.platform.idminter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.platform.idminter.steps.{
  IdentifierGenerator,
  SourceIdentifierEmbedder
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class IdMinterWorkerService[Destination](
  identifierGenerator: IdentifierGenerator,
  sender: BigMessageSender[Destination, Json],
  messageStream: BigMessageStream[Json],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
) extends Runnable
    with Logging {

  private val className = this.getClass.getSimpleName

  def run(): Future[Done] = {
    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    messageStream.foreach(className, processMessage)
  }

  def processMessage(json: Json): Future[Unit] = Future fromTry {
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers)
      updatedJson <- SourceIdentifierEmbedder.update(json, mintedIdentifiers)
      _ <- sender.sendT(updatedJson)
    } yield ()
  }
}
