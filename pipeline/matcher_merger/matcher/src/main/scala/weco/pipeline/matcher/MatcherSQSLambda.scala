package weco.pipeline.matcher

import weco.lambda.{
  ApplicationConfig,
  Downstream,
  SQSBatchResponseLambdaApp,
  SQSLambdaMessage,
  SQSLambdaMessageFailedRetryable,
  SQSLambdaMessageResult
}
import weco.pipeline.matcher.matcher.WorksMatcher
import weco.pipeline.matcher.models.MatcherResult

import scala.concurrent.Future

trait MatcherSQSLambda[Config <: ApplicationConfig]
    extends SQSBatchResponseLambdaApp[String, Config] {

  protected val worksMatcher: WorksMatcher
  protected val downstream: Downstream

  private implicit class MatcherResultOps(r: MatcherResult) {
    private def allIdentifiers = r.works.flatMap(_.identifiers)
    def allUnderlyingIdentifiers: Set[String] =
      allIdentifiers.map(_.identifier.underlying)
  }

  override def processMessages(
    messages: Seq[SQSLambdaMessage[String]]
  ): Future[Seq[SQSLambdaMessageResult]] = {
    val messagesMap: Map[String, String] = messages.map {
      message =>
        message.message -> message.messageId
    }.toMap
    // Do the matching.
    val resultsFuture: Future[Iterable[MatcherResult]] =
      worksMatcher.matchWorks(messagesMap.keySet.toSeq)
    // results now contains an iterable of successful MatcherResults.
    // A single MatcherResult contains a set of match groups,
    // each of which contains a set of affected ids

    // Next, we must do two things:
    // 1. notify downstream with all the MatcherResults.
    // 2. filter out any successful ids from the messagesMap and return the bad messageIds.
    resultsFuture.map {
      results: Iterable[MatcherResult] =>
        val identifiers = results.flatMap(_.allUnderlyingIdentifiers).toSet
        // If we have failed to notify downstream, we will throw an exception
        // this could be smarter, but for now we will just fail the whole batch
        if(results.map(downstream.notify(_)(MatcherResult.encoder)).exists(_.isFailure)){
            throw new RuntimeException("Failed to notify downstream")
        }
        findMissingMessages(messagesMap, identifiers)
    }
  }

  /** Given ...
    *   - a map of Work identifiers to SQS Message identifiers
    *   - and a set of found Work identifiers
    *
    * Return a seq of SQS failures for any messages not present in the found
    * list.
    */
  private def findMissingMessages(
    messagesMap: Map[String, String],
    foundIdentifiers: Set[String]
  ): Seq[SQSLambdaMessageFailedRetryable] = messagesMap
    .filterKeys(key => !foundIdentifiers.contains(key))
    .keys
    .map {
      identifier =>
        SQSLambdaMessageFailedRetryable(
          messageId = messagesMap(identifier),
          // At this point, we do not have specific details as to why it failed,
          // but we will have logged the specific errors deep in the process.
          error = new Error(s"Matcher failed for $identifier.")
        )
    } toSeq
}
