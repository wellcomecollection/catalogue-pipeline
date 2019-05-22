package uk.ac.wellcome.platform.matcher.storage

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException
import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.util.Try

class DynamoWorkNodeDao(dynamoDbClient: AmazonDynamoDB, dynamoConfig: DynamoConfig) extends Logging with WorkNodeDao {

  private val index = dynamoConfig.index

  override def put(work: WorkNode): Try[WorkNode] =
    Try {
      Scanamo.put(dynamoDbClient)(dynamoConfig.table)(work) match {
        case Some(Left(err)) =>
          val t = new Throwable(s"Error from Scanamo: $err")
          error(s"Error putting $work in DynamoDB", t)
          throw t
        case _ => work
      }
    }.recover {
      case exception: ProvisionedThroughputExceededException =>
        throw MatcherException(exception)
    }

  override def get(ids: Set[String]): Try[Set[WorkNode]] =
    Try {
      Scanamo
        .getAll[WorkNode](dynamoDbClient)(dynamoConfig.table)('id -> ids)
        .map {
          case Right(works) => works
          case Left(scanamoError) =>
            val exception = new RuntimeException(scanamoError.toString)
            error(
              s"An error occurred while retrieving all workIds=$ids from DynamoDB",
              exception)
            throw exception
        }
    }.recover {
      case exception: ProvisionedThroughputExceededException =>
        throw MatcherException(exception)
    }

  override def getByComponentId(componentId: String): Try[Seq[WorkNode]] =
    Try {
      Scanamo
        .queryIndex[WorkNode](dynamoDbClient)(dynamoConfig.table, index)(
        'componentId -> componentId)
        .map {
          case Right(record) => {
            record
          }
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
