package weco.pipeline.matcher.storage

import grizzled.slf4j.Logging
import org.scanamo.{DynamoFormat, Scanamo, Table}
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.storage.dynamo.DynamoConfig
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.models.WorkNode

import scala.concurrent.{ExecutionContext, Future}

class WorkNodeDao(dynamoClient: DynamoDbClient, dynamoConfig: DynamoConfig)(
  implicit ec: ExecutionContext,
  format: DynamoFormat[WorkNode])
    extends Logging {

  private val scanamo = Scanamo(dynamoClient)
  private val nodes = Table[WorkNode](dynamoConfig.tableName)
  private val index = nodes.index(dynamoConfig.indexName)

  def put(workNodes: Set[WorkNode]): Future[Unit] =
    Future { scanamo.exec(nodes.putAll(workNodes)) }
      .recover {
        case exception: ProvisionedThroughputExceededException =>
          throw MatcherException(exception)
      }

  def get(ids: Set[CanonicalId]): Future[Set[WorkNode]] =
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
