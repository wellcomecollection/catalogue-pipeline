package uk.ac.wellcome.platform.matcher.storage

import grizzled.slf4j.Logging
import org.scanamo.{DynamoFormat, Scanamo, Table}
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.storage.dynamo.DynamoBatchWriter
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import weco.catalogue.internal_model.identifiers.CanonicalID

import scala.concurrent.{ExecutionContext, Future}

class WorkNodeDao(dynamoClient: DynamoDbClient, dynamoConfig: DynamoConfig)(
  implicit ec: ExecutionContext,
  format: DynamoFormat[WorkNode])
    extends Logging {

  private val scanamo = Scanamo(dynamoClient)
  private val nodes = Table[WorkNode](dynamoConfig.tableName)
  private val index = nodes.index(dynamoConfig.indexName)

  private val batchWriter = new DynamoBatchWriter[WorkNode](
    dynamoConfig
  )(ec, dynamoClient, format)

  def put(workNodes: Set[WorkNode]): Future[Unit] =
    batchWriter
      .batchWrite(workNodes.toSeq)
      .recover {
        case exception: ProvisionedThroughputExceededException =>
          throw MatcherException(exception)
      }

  def get(ids: Set[CanonicalID]): Future[Set[WorkNode]] =
    Future {
      scanamo
        .exec { nodes.getAll("id" in ids) }
        .map {
          case Right(works) => works
          case Left(scanamoError) => {
            val exception = new RuntimeException(scanamoError.toString)
            error(
              s"An error occurred while retrieving all workIds=$ids from DynamoDB",
              exception)
            throw exception
          }
        }
    }.recover {
      case exception: ProvisionedThroughputExceededException =>
        throw MatcherException(exception)
    }

  def getByComponentIds(setIds: Set[String]): Future[Set[WorkNode]] =
    Future.sequence(setIds.map(getByComponentId)).map(_.flatten)

  private def getByComponentId(componentId: String) =
    Future {
      scanamo
        .exec { index.query("componentId" === componentId) }
        .map {
          case Right(record) => { record }
          case Left(scanamoError) => {
            val exception = new RuntimeException(scanamoError.toString)
            error(
              s"An error occurred while retrieving byComponentId=$componentId from DynamoDB",
              exception
            )
            throw exception
          }
        }
    }.recover {
      case exception: ProvisionedThroughputExceededException =>
        throw MatcherException(exception)
    }
}
