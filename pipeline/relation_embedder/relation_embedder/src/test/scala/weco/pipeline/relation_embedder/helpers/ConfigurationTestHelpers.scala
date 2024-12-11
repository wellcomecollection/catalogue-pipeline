package weco.pipeline.relation_embedder.helpers

import com.typesafe.config.{Config, ConfigFactory}
import weco.fixtures.TestWith
import weco.pipeline.relation_embedder.lib.LambdaConfiguration

trait ConfigurationTestHelpers {

  class TestLambdaConfiguration(
                                 override val baseConfig: Config,
                                 override val applicationConfig: Config,
                                 override val lambdaConfig: Config
                               ) extends LambdaConfiguration

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
                          )(testWith: TestWith[Config, R]): R = {
    testWith(new TestLambdaConfiguration(
      baseConfig,
      applicationConfig,
      lambdaConfig
    ).config)
  }
}
