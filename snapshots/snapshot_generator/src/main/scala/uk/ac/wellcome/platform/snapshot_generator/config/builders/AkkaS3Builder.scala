package uk.ac.wellcome.platform.snapshot_generator.config.builders

import akka.actor.ActorSystem
import akka.stream.alpakka.s3.S3Settings
import com.amazonaws.auth.{
  AWSCredentialsProvider,
  AWSStaticCredentialsProvider,
  BasicAWSCredentials,
  DefaultAWSCredentialsProviderChain
}
import com.amazonaws.regions.AwsRegionProvider
import com.typesafe.config.Config
import grizzled.slf4j.Logging
import uk.ac.wellcome.config.models.AWSClientConfig
import uk.ac.wellcome.typesafe.config.builders.AWSClientConfigBuilder

object AkkaS3Builder extends AWSClientConfigBuilder with Logging {

  def buildAkkaS3Settings(config: Config)(
    implicit actorSystem: ActorSystem): S3Settings =
    buildAkkaS3Settings(
      buildAWSClientConfig(config, namespace = "s3")
    )

  def buildAkkaS3Settings(awsClientConfig: AWSClientConfig)(
    implicit actorSystem: ActorSystem): S3Settings = {
    val regionProvider =
      new AwsRegionProvider {
        def getRegion: String = awsClientConfig.region
      }

    val credentialsProvider = if (awsClientConfig.endpoint.isEmpty) {
      DefaultAWSCredentialsProviderChain.getInstance()
    } else {
      new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(
          awsClientConfig.accessKey.get,
          awsClientConfig.secretKey.get)
      )
    }

    val endpointUrl = awsClientConfig.endpoint match {
      case Some(e) => if (e.isEmpty) None else Some(e)
      case None    => None
    }

    akkaS3Settings(
      credentialsProvider = credentialsProvider,
      regionProvider = regionProvider,
      endpointUrl = endpointUrl
    )
  }

  private def akkaS3Settings(credentialsProvider: AWSCredentialsProvider,
                             regionProvider: AwsRegionProvider,
                             endpointUrl: Option[String])(
                              implicit actorSystem: ActorSystem): S3Settings = {
    val settings = S3Settings().withCredentialsProvider(credentialsProvider).withS3RegionProvider(regionProvider).withPathStyleAccess(true)
    endpointUrl match {
      case Some(e) => settings.withEndpointUrl(e)
      case None => settings
    }
  }
}
