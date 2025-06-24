package weco.lambda.matchers

import org.scalatest.matchers.{MatchResult, Matcher}
import weco.lambda.{SQSLambdaMessage, SQSLambdaMessageResult}

trait LambdaResultMatchers[T] {
  class HaveTheSameId(message: SQSLambdaMessage[T])
      extends Matcher[SQSLambdaMessageResult] {
    override def apply(result: SQSLambdaMessageResult): MatchResult =
      MatchResult(
        message.messageId == result.messageId,
        s"""Result ${result.messageId} did not have the same id as the message ${message.messageId}"""",
        s"""Message ids ${message.messageId} matched"""
      )
  }

  class HaveTheSameIdsMatcher(messages: Seq[SQSLambdaMessage[T]])
      extends Matcher[Seq[SQSLambdaMessageResult]] {
    override def apply(results: Seq[SQSLambdaMessageResult]): MatchResult = {
      val resultIds = results.map(_.messageId)
      val messageIds = messages.map(_.messageId)
      val leftNotRight = resultIds diff messageIds
      val rightNotLeft = messageIds diff resultIds

      MatchResult(
        leftNotRight.isEmpty && rightNotLeft.isEmpty,
        s"""Results ${resultIds} did not have the same id as the messages ${messageIds}"""",
        s"""Message ids ${resultIds} matched"""
      )
    }
  }
  def haveTheSameIdsAs(messages: Seq[SQSLambdaMessage[T]]) =
    new HaveTheSameIdsMatcher(messages)
}
