package weco.pipeline.relation_embedder.lib

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}

trait Configuration {
  val config: Config
}

trait LambdaConfiguration extends Configuration {
  private val defaultResolveFromFile: String = "/tmp/config"
  private val defaultApplicationConfig: String = "application.conf"

  private val lambdaConfigFile: File =
    new File(defaultResolveFromFile)

  protected val baseConfig: Config =
    ConfigFactory.load()

  protected val applicationConfig: Config =
    ConfigFactory.parseResources(defaultApplicationConfig)

  protected val lambdaConfig: Config = if (lambdaConfigFile.exists()) {
    ConfigFactory.parseFile(lambdaConfigFile)
  } else {
    ConfigFactory.empty()
  }

  lazy val config = lambdaConfig
    .withFallback(applicationConfig)
    .withFallback(baseConfig)
    .resolve()
}
