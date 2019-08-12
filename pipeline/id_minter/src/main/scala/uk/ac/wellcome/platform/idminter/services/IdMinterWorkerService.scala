package uk.ac.wellcome.platform.idminter.services

import akka.Done
import io.circe.Json
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.MessageStream
import uk.ac.wellcome.platform.idminter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.platform.idminter.steps.IdEmbedder
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class IdMinterWorkerService(
  idEmbedder: IdEmbedder,
  sender: BigMessageSender[SNSConfig, Json],
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

    messageStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  def processMessage(json: Json): Future[Unit] =
    for {
      identifiedJson <- idEmbedder.embedId(json)
      _ <- Future.fromTry { sender.sendT(identifiedJson) }
    } yield ()
}
