package weco.pipeline.matcher

import io.circe.Decoder
import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda.behaviours.LambdaBehaviours
import weco.lambda.Downstream
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.matcher.WorksMatcher
import weco.lambda.helpers.{LambdaFixtures, MemoryDownstream}
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.models.MatcherResult

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with LoneElement
    with ScalaFutures
    with MatcherFixtures
    with LambdaFixtures
    with LambdaBehaviours[String, MatcherConfig, MatcherResult, Set[String]]
    with MemoryDownstream {

  protected implicit val outputDecoder: Decoder[MatcherResult] =
    MatcherResult.decoder

  implicit class MatcherResultOps(result: MatcherResult) {
    def toWorkSet: Set[String] =
      result.works.flatMap(id => id.identifiers.map(_.identifier.toString()))
  }

  override protected def convertForComparison(
    results: Seq[MatcherResult]
  ): Seq[Set[String]] = results.map(_.toWorkSet)

  /** Stub class representing the Lambda interface, which can be connected to a
    * dummy matcher to test the behaviour of the lambda when faced with various
    * responses.
    */
  case class StubLambda(
    worksMatcher: WorksMatcher,
    downstream: Downstream
  ) extends MatcherSQSLambda[MatcherConfig]
      with MatcherConfigurable

  private val baadcafe = SQSTestLambdaMessage(message = "baadcafe")
  private val baadd00d = SQSTestLambdaMessage(message = "baadd00d")
  private val g00dcafe = SQSTestLambdaMessage(message = "g00dcafe")
  private val g00dd00d = SQSTestLambdaMessage(message = "g00dd00d")

  private val LambdaBuilder: WorksMatcher => Downstream => StubLambda =
    StubLambda.curried

  describe("When all messages fail") {
    it should behave like aFailingInvocation(
      LambdaBuilder(MatcherStub(Nil)),
      Seq(baadcafe, baadd00d)
    )
  }
  describe("When some messages fail") {
    it should behave like aPartialSuccess(
      LambdaBuilder(MatcherStub(Seq(Set(Set(g00dcafe.message))))),
      messages = Seq(g00dcafe, baadd00d),
      failingMessages = Seq(baadd00d),
      outputs = Seq(Set(g00dcafe.message))
    )
  }
  describe("When everything is successful") {
    it should behave like aTotalSuccess(
      LambdaBuilder(MatcherStub(Seq(Set(Set("g00dcafe"), Set("g00dd00d"))))),
      messages = Seq(g00dcafe, g00dd00d),
      outputs = () => Seq(Set("g00dcafe", "g00dd00d"))
    )
  }

  describe("When matcher results contain other identifiers") {
    info(
      "a message only counts as a failure if its body cannot be found in any matcher result"
    )
    info(
      "*all* ids found in all matcher results are notified downstream, even if not mentioned in the messages from upstream"
    )
    val beefcafe = SQSTestLambdaMessage(message = "beefcafe")
    val f00df00d = SQSTestLambdaMessage(message = "f00df00d")
    val f00dfeed = SQSTestLambdaMessage(message = "f00dfeed")
    // Five messages went in,
    val messages = Seq(
      baadd00d, // fails
      g00dcafe,
      beefcafe,
      f00df00d,
      f00dfeed
    )
    // one fails and only two works come out.
    // (perhaps something went wrong with the handling of those messages)
    // However, the matches returned by the two works cover
    // the two remaining works, so they are deemed to have succeeded
    val matcherLambda = LambdaBuilder(
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
    )

    it should behave like aPartialSuccess(
      matcherLambda,
      messages = messages,
      failingMessages = Seq(baadd00d),
      outputs = Seq(
        Set(
          g00dcafe.message,
          beefcafe.message,
          "cafef00d"
        ), // cafef00d was not one of the input messages, but it was found in the match for one of them
        Set(f00df00d.message, f00dfeed.message)
      )
    )
  }
}
