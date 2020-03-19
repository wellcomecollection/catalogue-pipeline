package uk.ac.wellcome.platform.idminter.services

import akka.Done
import akka.stream.scaladsl.Flow
import com.amazonaws.services.sqs.model.Message
import io.circe.Json
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.steps.{
  IdentifierGenerator,
  SourceIdentifierScanner
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class IdMinterWorkerService[Destination](
  identifierGenerator: IdentifierGenerator,
  sender: BigMessageSender[Destination, Json],
  messageStream: BigMessageStream[Json],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
) extends Runnable {

  private val className = this.getClass.getSimpleName

  def run(): Future[Done] = {
    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    messageStream.runStream(
      className,
      _.via(extractIdentifiers)
        .via(synchroniseIds)
        .via(embedIdentifiers)
        .via(sendMinted)
        .map { case (msg, _) => msg }
    )
  }

  private def extractIdentifiers =
    Flow[(Message, Json)].map {
      case (msg, json) =>
        (msg, json, SourceIdentifierScanner.scan(json).get)
    }

  private def synchroniseIds =
    Flow[(Message, Json, List[SourceIdentifier])].map {
      case (msg, json, ids) =>
        (msg, json, identifierGenerator.retrieveOrGenerateCanonicalIds(ids).get)
    }

  private def embedIdentifiers =
    Flow[(Message, Json, Map[SourceIdentifier, Identifier])].map {
      case (msg, json, identifiersTable) =>
        (msg, SourceIdentifierScanner.update(json, identifiersTable).get)
    }

  private def sendMinted =
    Flow[(Message, Json)].map {
      case (msg, json) =>
        (msg, sender.sendT(json).get)
    }
}
