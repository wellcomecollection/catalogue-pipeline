package weco.pipeline.matcher.matcher

import grizzled.slf4j.Logging
import io.circe.Json
import org.scanamo.DynamoFormat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.elasticsearch.typesafe.{ElasticBuilder, ElasticConfig}
import weco.pipeline.matcher.models.{MatcherResult, WorkNode, WorkStub}
import weco.pipeline.matcher.services.LockingBuilder
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}
import weco.pipeline.matcher.storage.elastic.ElasticWorkStubRetriever
import weco.pipeline_storage.RetrieverMultiResult
import weco.storage.dynamo.DynamoConfig
import weco.storage.locking.dynamo.DynamoLockDaoConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Performs the matching for a bundle of works that are currently stored in
  * Elasticsearch.
  */
class StoredWorksMatcher(
  retriever: ElasticWorkStubRetriever,
  workMatcher: WorkMatcher
)(implicit ec: ExecutionContext)
    extends Logging {

  def matchWorks(workIds: Seq[String]): Future[Iterable[MatcherResult]] =
    retrieveWorks(workIds).map(matchWorks).flatMap(collectSuccessfulResults)

  /*
   * Fetch all the work stubs associated with this batch of identifiers.
   * This method only returns those works that have been successfully
   * retrieved, and logs any that were not found.
   *
   * There is no need to action any missing works at this point,
   * because the caller
   * */
  private def retrieveWorks(workIds: Seq[String]): Future[Iterable[WorkStub]] =
    retriever(workIds).flatMap {
      result: RetrieverMultiResult[WorkStub] =>
        result.notFound.values.foreach {
          // Log if a work stub could not be retrieved.
          exception => error(exception)
        }
        Future.successful(result.found.values)
    }

  private def matchWorks(
    workStubs: Iterable[WorkStub]
  ): Iterable[Future[MatcherResult]] =
    workStubs.map(workMatcher.matchWork)

  private def collectSuccessfulResults(
    futures: Iterable[Future[MatcherResult]]
  )(implicit ec: ExecutionContext): Future[Iterable[MatcherResult]] = {
    val result = Future.sequence(
      futures.map {
        future =>
          future.transform {
            case Success(value) => Success(Some(value))
            case Failure(exception) =>
              error(exception)
              Success(None)
          }
      }
    )

    result.map(_.flatten)
  }

}

object StoredWorksMatcher {
  def apply(
    elasticConfig: ElasticConfig,
    elasticIndex: String,
    dynamoConfig: DynamoConfig,
    dynamoLockDaoConfig: DynamoLockDaoConfig
  )(
    implicit ec: ExecutionContext,
    format: DynamoFormat[WorkNode]
  ): StoredWorksMatcher = {

    val retriever =
      new ElasticWorkStubRetriever(
        client = ElasticBuilder.buildElasticClient(elasticConfig),
        index = elasticIndex
      )

    new StoredWorksMatcher(
      retriever,
      WorkMatcher(dynamoConfig, dynamoLockDaoConfig)
    )
  }

}
