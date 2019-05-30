package uk.ac.wellcome.platform.idminter.services

import akka.Done
import io.circe.Json
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.message.MessageStream
import uk.ac.wellcome.platform.idminter.config.models.{IdentifiersTableConfig, RDSClientConfig}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.platform.idminter.steps.IdEmbedder
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IdMinterWorkerService[Destination](
  idEmbedder: IdEmbedder,
  messageSender: MessageSender[Destination],
  messageStream: MessageStream[Json],
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

    messageStream.foreach(this.getClass.getSimpleName,
      message => Future.fromTry { processMessage(message) }
    )
  }

  def processMessage(json: Json): Try[Unit] =
    for {
      identifiedJson <- idEmbedder.embedId(json)
      _ <- messageSender.sendT(identifiedJson)
    } yield ()
}
