package weco.lambda

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File

trait ApplicationConfig {}

trait ConfigurationBuilder[C, T <: ApplicationConfig] {
  protected val rawConfig: C

  def build(rawConfig: C): T
  def config: T = build(rawConfig)
}

trait TypesafeConfigurable[T <: ApplicationConfig]
    extends ConfigurationBuilder[Config, T] {
  def build(rawConfig: Config): T
}

trait LambdaConfigurable[T <: ApplicationConfig]
    extends TypesafeConfigurable[T] {
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

  lazy val rawConfig = lambdaConfig
    .withFallback(applicationConfig)
    .withFallback(baseConfig)
    .resolve()
}
