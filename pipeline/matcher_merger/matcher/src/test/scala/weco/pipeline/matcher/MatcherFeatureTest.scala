package weco.pipeline.matcher

import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda.{Downstream, SQSLambdaMessage, SQSLambdaMessageFailedRetryable, SQSLambdaMessageResult}
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.matcher.WorksMatcher
import weco.lambda.helpers.DownstreamHelper
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.models.MatcherResult

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with LoneElement
    with ScalaFutures
    with MatcherFixtures
    with DownstreamHelper {

  private object SQSTestLambdaMessage {
    def apply[T](message: T): SQSLambdaMessage[T] =
      SQSLambdaMessage(messageId = randomUUID.toString, message = message)
  }

  /** Stub class representing the Lambda interface, which can be connected to a
    * dummy matcher to test the behaviour of the lambda when faced with various
    * responses.
    */
  private case class StubLambda(
    worksMatcher: WorksMatcher,
    downstream: Downstream
  ) extends MatcherSQSLambda[MatcherConfig]
      with MatcherConfigurable

  describe("when everything is successful") {
    val matcher = MatcherStub(Seq(Set(Set("g00dcafe"), Set("g00dd00d"))))
    val downstream = new MemoryDownstream
    val sut = StubLambda(matcher, downstream)
    whenReady(
      sut.processMessages(messages =
        Seq(
          SQSTestLambdaMessage(message = "g00dcafe"),
          SQSTestLambdaMessage(message = "g00dd00d")
        )
      )
    ) {
      response =>
        it("returns no results") {
          response shouldBe empty
        }
        it("sends all the identifiers downstream") {
          downstream.msgSender
            .getMessages[MatcherResult]
            .map(matcherResult => matcherResult.works
              .flatMap(id => id.identifiers.map(_.identifier.toString()))) should contain theSameElementsAs Seq(Set("g00dcafe", "g00dd00d"))
        }
    }
  }
  
  describe("when all messages fail to process") {
    val messages = Seq(
      SQSTestLambdaMessage(message = "baadcafe"),
      SQSTestLambdaMessage(message = "baadd00d")
    )
    val downstream = new MemoryDownstream
    val sut = StubLambda(MatcherStub(Nil), downstream)

    whenReady(
      sut.processMessages(messages = messages)
    ) {
      results: Seq[SQSLambdaMessageResult] =>
        it("returns a seq of BatchItemFailure responses") {
          results.map(_.messageId) should contain theSameElementsAs messages
            .map(
              _.messageId
            )
          all(results) shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it("does not notify downstream") {
          downstream.msgSender
            .getMessages[MatcherResult].length shouldBe 0
        }
    }

  }

  describe("when some messages fail to process") {
    val messages = Seq(
      SQSTestLambdaMessage(message = "g00dcafe"),
      SQSTestLambdaMessage(message = "baadd00d")
    )
    val downstream = new MemoryDownstream
    val sut = StubLambda(MatcherStub(Seq(Set(Set("g00dcafe")))), downstream)

    whenReady(
      sut
        .processMessages(messages = messages)
    ) {
      results: Seq[SQSLambdaMessageResult] =>
        it("returns BatchItemFailure responses only for failing ids") {
          results.loneElement.messageId shouldBe messages(1).messageId
          results.loneElement shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it("notifies downstream only for successful ids") {
          downstream.msgSender
            .getMessages[MatcherResult]
            .map(matcherResult => matcherResult.works
              .flatMap(id => id.identifiers
                .map(_.identifier.toString()))) should contain theSameElementsAs  Seq(Set("g00dcafe"))
        }
    }
  }

  describe("when matcher results contain other identifiers") {
    val messages = Seq(
      SQSTestLambdaMessage(message = "baadd00d"), // fails
      SQSTestLambdaMessage(message = "g00dcafe"),
      SQSTestLambdaMessage(message = "beefcafe"),
      SQSTestLambdaMessage(message = "f00df00d"),
      SQSTestLambdaMessage(message = "f00dfeed")
    )
    // Five messages went in, one fails and only two works come out.
    // (perhaps something went wrong with the handling of those messages)
    // However, the matches returned by the two works cover
    // the two remaining works, so they are deemed to have succeeded
    val matcher =
      MatcherStub(
        Seq(
          Set(
            Set("g00dcafe", "beefcafe", "cafef00d")
          ),
          Set(
            Set("f00df00d"),
            Set("f00dfeed")
          )
        )
      )
    val downstream = new MemoryDownstream
    val sut = StubLambda(matcher, downstream)

    whenReady(
      sut.processMessages(messages = messages)
    ) {
      results: Seq[SQSLambdaMessageResult] =>
        it(
          "treats a message as a failure if its body cannot be found in any matcher result"
        ) {
          // baadd00d is first in the input list, and is absent from any output results
          results.loneElement.messageId shouldBe messages.head.messageId
          results.loneElement shouldBe a[SQSLambdaMessageFailedRetryable]
        }
        it(
          "notifies downstream of *all* ids found in all matcher results, even if not mentioned in the messages from upstream"
        ) {
          downstream.msgSender
            .getMessages[MatcherResult]
            .map(matcherResult => matcherResult.works
              .flatMap(id => id.identifiers
                .map(_.identifier.toString()))) should contain theSameElementsAs Seq(
                  Set("g00dcafe", "beefcafe", "cafef00d"), // cafef00d was not one of the input messages, but it was found in the match for one of them
                  Set("f00df00d", "f00dfeed")
                )
        }
    }
  }
}
