package uk.ac.wellcome.platform.idminter.services

import akka.Done
import io.circe.Json
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.platform.idminter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.platform.idminter.steps.{
  IdEmbedder,
  IdentifierGenerator,
  SourceIdentifierScanner
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class IdMinterWorkerService[Destination](
  idEmbedder: IdEmbedder,
  identifierGenerator: IdentifierGenerator,
  sender: BigMessageSender[Destination, Json],
  messageStream: BigMessageStream[Json],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] = {
    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    messageStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  def processMessage(json: Json): Future[Unit] =
    Future {
      for {
        sourceIdentifiers <- SourceIdentifierScanner.scan(json)
        canonicalIds <- identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers)
        identifiedJson <- SourceIdentifierScanner.update(json, canonicalIds)
        _ <- sender.sendT(identifiedJson)
      } yield ()
    }
}
