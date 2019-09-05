package uk.ac.wellcome.platform.matcher.storage

import grizzled.slf4j.Logging
import javax.naming.ConfigurationException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException
import org.scanamo.{Scanamo, Table}
import org.scanamo.error.DynamoReadError
import org.scanamo.syntax._
import org.scanamo.auto._

import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.concurrent.{ExecutionContext, Future}

class WorkNodeDao(dynamoClient: AmazonDynamoDB, dynamoConfig: DynamoConfig)(
  implicit ec: ExecutionContext)
    extends Logging {

  private val scanamo = Scanamo(dynamoClient)
  private val nodes = Table[WorkNode](dynamoConfig.tableName)
  private val index = nodes.index(
    dynamoConfig.maybeIndexName.getOrElse {
      throw new ConfigurationException("Index not specified")
    }
  )

  def put(work: WorkNode): Future[Option[Either[DynamoReadError, WorkNode]]] =
    Future { scanamo.exec { nodes.put(work) } }
      .recover {
        case exception: ProvisionedThroughputExceededException =>
          throw MatcherException(exception)
      }

  def get(ids: Set[String]): Future[Set[WorkNode]] =
    Future {
      scanamo
        .exec { nodes.getAll('id -> ids) }
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
        .exec { index.query('componentId -> componentId) }
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
