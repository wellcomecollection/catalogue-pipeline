package uk.ac.wellcome.platform.id_minter_works.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import akka.Done
import grizzled.slf4j.Logging
import io.circe.{Decoder, Json}

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
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

class IdMinterWorkerService[Destination](
  identifierGenerator: IdentifierGenerator,
  sender: MessageSender[Destination],
  messageStream: SQSStream[NotificationMessage],
  jsonRetriever: Retriever[Json],
  workIndexer: Indexer[Work[Identified]],
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

    messageStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  def processMessage(message: NotificationMessage): Future[Unit] =
    jsonRetriever(message.body)
      .flatMap { json => Future.fromTry(embedIds(json)) }
      .flatMap { updatedJson => Future.fromTry(decodeJson[Work[Identified]](updatedJson)) }
      .flatMap { work =>
        workIndexer.index(work).flatMap {
          case Left(failedDocuments) =>
            Future.failed(new Exception(s"Failed indexing: $failedDocuments"))
          case _ => Future.fromTry(sender.send(work.id))
        }
      }

  def embedIds(json: Json): Try[Json] =
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers)
      updatedJson <- SourceIdentifierEmbedder.update(json, mintedIdentifiers)
    } yield updatedJson

  def decodeJson[T](json: Json)(implicit decoder: Decoder[T]): Try[T] =
    ???
}
