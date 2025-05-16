package weco.pipeline.matcher.services

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.storage.locking.dynamo.{
  DynamoLockDao,
  DynamoLockDaoConfig,
  DynamoLockingService
}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object LockingBuilder {
  def buildDynamoLockingService[Out, OutMonad[_]](
    config: DynamoLockDaoConfig
  )(implicit ec: ExecutionContext): DynamoLockingService[Out, OutMonad] = {
    implicit val dynamoLockDao: DynamoLockDao = new DynamoLockDao(
      client = DynamoDbClient.builder().build(),
      config = config
    )
    new DynamoLockingService()
  }
}
