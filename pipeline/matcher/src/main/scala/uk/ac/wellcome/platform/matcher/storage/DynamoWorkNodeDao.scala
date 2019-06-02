package uk.ac.wellcome.platform.matcher.storage

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException
import com.gu.scanamo.Scanamo
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.storage.dynamo.{DynamoConfig, DynamoDao}

import scala.concurrent.{ExecutionContext, Future}

class DynamoWorkNodeDao(
  dynamoClient: AmazonDynamoDB,
  dynamoConfig: DynamoConfig)(implicit ec: ExecutionContext)
    extends Logging {

  private val index = dynamoConfig.index

  val dynamoDao = new DynamoDao[String, WorkNode](
    dynamoClient = dynamoClient,
    dynamoConfig = dynamoConfig
  )

  def put(workNode: WorkNode): dynamoDao.PutResult = dynamoDao.put(workNode)

  def get(ids: Set[String]): dynamoDao.GetResult =
    dynamoDao.executeReadOps(
      id = ids.toString(),
      ops = dynamoDao.table.getAll('id -> ids)
    )

  def getByComponentIds(setIds: Set[String]): Future[Set[WorkNode]] =
    Future.sequence(setIds.map(getByComponentId)).map(_.flatten)

  private def getByComponentId(componentId: String): Future[List[WorkNode]] =
    Future {
      Scanamo
        .queryIndex[WorkNode](dynamoClient)(dynamoConfig.table, index)(
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
