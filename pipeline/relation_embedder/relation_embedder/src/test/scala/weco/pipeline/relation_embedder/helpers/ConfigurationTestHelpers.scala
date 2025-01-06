package weco.pipeline.relation_embedder.helpers

import com.typesafe.config.{Config, ConfigFactory}
import weco.fixtures.TestWith
import weco.pipeline.relation_embedder.lib.{
  ApplicationConfig,
  LambdaConfigurable
}

trait ConfigurationTestHelpers {

  case class TestAppConfiguration(
    config1: Option[String] = None,
    config2: Option[String] = None,
    config3: Option[String] = None
  ) extends ApplicationConfig

  class TestLambdaConfiguration(
    override val baseConfig: Config,
    override val applicationConfig: Config,
    override val lambdaConfig: Config
  ) extends LambdaConfigurable[TestAppConfiguration] {
    import weco.typesafe.config.builders.EnrichConfig._

    override def build(rawConfig: Config): TestAppConfiguration = {
      TestAppConfiguration(
        config1 = rawConfig.getStringOption("config1"),
        config2 = rawConfig.getStringOption("config2"),
        config3 = rawConfig.getStringOption("config3")
      )
    }
  }

  def createConfig(configString: String): Config = {
    ConfigFactory.parseString(configString.stripMargin)
  }

  implicit class AsConfig(config: String) {
    def asConfig: Config = createConfig(config)
  }

  val emptyConfig = ConfigFactory.empty()

  def withLayeredConfig[R](
    baseConfig: Config = emptyConfig,
    applicationConfig: Config = emptyConfig,
    lambdaConfig: Config = emptyConfig
  )(testWith: TestWith[TestAppConfiguration, R]): R = {
    testWith(
      new TestLambdaConfiguration(
        baseConfig,
        applicationConfig,
        lambdaConfig
      ).config
    )
  }
}
