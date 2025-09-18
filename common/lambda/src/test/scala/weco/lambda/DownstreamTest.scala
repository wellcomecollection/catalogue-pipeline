package weco.lambda

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.lambda.helpers.ConfigurationTestHelpers
import weco.messaging.sns.SNSConfig

class DownstreamTest
    extends AnyFunSpec
    with ConfigurationTestHelpers
    with Matchers {

  describe("DownstreamBuilder") {
    it("builds a StdOut downstream target") {
      val config =
        """
          |downstream.target = "stdio"
          |""".asConfig

      DownstreamBuilder.buildDownstreamTarget(config) shouldBe StdOut
    }

    it("builds an SNS downstream target") {
      val config =
        """
          |downstream.target = "sns"
          |aws.sns.topic.arn = "arn:aws:sns:eu-west-1:123456789012:my-topic"
          |""".asConfig

      DownstreamBuilder.buildDownstreamTarget(config) shouldBe SNS(
        config = SNSConfig(
          topicArn = "arn:aws:sns:eu-west-1:123456789012:my-topic"
        )
      )
    }

    it(
      "builds a StdOut downstream target if no downstream target is specified"
    ) {
      val config =
        """
          |""".asConfig

      DownstreamBuilder.buildDownstreamTarget(config) shouldBe StdOut
    }

    it("throws an exception if the downstream target is not recognised") {
      val config =
        """
          |downstream.target = "invalid"
          |""".asConfig

      intercept[IllegalArgumentException] {
        DownstreamBuilder.buildDownstreamTarget(config)
      }
    }
  }
}
