package uk.ac.wellcome.platform.id_minter.services

import akka.Done
import grizzled.slf4j.Logging
import io.circe.{Decoder, Json}
import uk.ac.wellcome.json.JsonUtil
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream._
import uk.ac.wellcome.pipeline_storage.{Indexer, PipelineStorageConfig, Retriever}
import uk.ac.wellcome.platform.id_minter.config.models.{IdentifiersTableConfig, RDSClientConfig}
import uk.ac.wellcome.platform.id_minter.database.TableProvisioner
import uk.ac.wellcome.platform.id_minter.steps.{IdentifierGenerator, SourceIdentifierEmbedder}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IdMinterWorkerService[Destination](
                                          msgStream: SQSStream[NotificationMessage],
                                          indexer: Indexer[Work[Identified]],
                                          config: PipelineStorageConfig,
                                          messageSender: MessageSender[Destination],
                                          identifierGenerator: IdentifierGenerator,
                                          jsonRetriever: Retriever[Json],
                                          rdsClientConfig: RDSClientConfig,
                                          identifiersTableConfig: IdentifiersTableConfig
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {
  val tableProvisioner = new TableProvisioner(
    rdsClientConfig = rdsClientConfig
  )

  def run(): Future[Done] = {
    for {
      _ <- Future.fromTry(Try(tableProvisioner.provision(
        database = identifiersTableConfig.database,
        tableName = identifiersTableConfig.tableName
      )))
      _ <- indexer.init()
      _ <- msgStream.runStream(
        this.getClass.getSimpleName,
        source =>
          source
            .via(batchRetrieveFlow(config, jsonRetriever))
            .via(processFlow(config, bundle => processMessage(bundle.item)))
            .via(
              broadcastAndMerge(
                batchIndexAndSendFlow(
                  config,
                  (doc: Work[Identified]) =>
                    sendIndexable(messageSender)(doc),
                  indexer),
                noOutputFlow))
      )
    } yield Done
  }

  def processMessage(
    json:Json): Future[List[Work[Identified]]] = for {
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
