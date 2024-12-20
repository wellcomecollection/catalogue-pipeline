package weco.lambda

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers
import weco.lambda.helpers.ConfigurationTestHelpers

class ConfigurationTest
    extends AnyFunSpec
    with ConfigurationTestHelpers
    with Matchers {

  it("loads the base configuration") {
    val baseConfig =
      """
        |config1 = "value"
        |""".asConfig

    withLayeredConfig(
      baseConfig = baseConfig
    ) {
      config =>
        config mustBe TestAppConfiguration(
          config1 = Some("value")
        )
    }
  }

  it("overrides the base configuration with the application configuration") {
    withLayeredConfig(
      baseConfig = """
          |config1 = "valueFromBaseForConfig1"
          |config2 = "valueFromBaseForConfig2"
          |""".asConfig,
      applicationConfig = """
          |config1 = "valueFromApplicationForConfig1"
          |""".asConfig
    ) {
      config =>
        config mustBe TestAppConfiguration(
          config1 = Some("valueFromApplicationForConfig1"),
          config2 = Some("valueFromBaseForConfig2")
        )
    }
  }

  it("overrides the application configuration with the lambda configuration") {
    withLayeredConfig(
      baseConfig = """
          |config1 = "valueFromApplicationForConfig1"
          |config2 = "valueFromBaseForConfig2"
          |config3 = "valueFromApplicationForConfig3"
          |""".asConfig,
      applicationConfig = """
          |config1 = "valueFromLambdaForConfig1"
          |""".asConfig,
      lambdaConfig = """
          |config1 = "valueFromLambdaForConfig1"
          |""".asConfig
    ) {
      config =>
        config mustBe TestAppConfiguration(
          config1 = Some("valueFromLambdaForConfig1"),
          config2 = Some("valueFromBaseForConfig2"),
          config3 = Some("valueFromApplicationForConfig3")
        )
    }
  }

  it(
    "resolves values in the application config from the lambda configuration"
  ) {
    withLayeredConfig(
      applicationConfig = s"""
         |config1 = $${?config2}
         |""".asConfig,
      lambdaConfig = """
          |config1 = "valueFromLambdaForConfig2"
          |""".asConfig
    ) {
      config =>
        config mustBe TestAppConfiguration(
          config1 = Some("valueFromLambdaForConfig2")
        )
    }
  }
}
