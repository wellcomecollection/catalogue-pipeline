package uk.ac.wellcome.platform.snapshot_generator.fixtures

import akka.actor.ActorSystem
import akka.stream.alpakka.s3.S3Settings
import uk.ac.wellcome.config.models.AWSClientConfig
import uk.ac.wellcome.platform.snapshot_generator.config.builders.AkkaS3Builder
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.fixtures.TestWith

trait AkkaS3 extends S3Fixtures {

  def withS3AkkaSettings[R](endpoint: String)(
    testWith: TestWith[S3Settings, R])(implicit actorSystem: ActorSystem): R = {
    val s3AkkaClient = AkkaS3Builder.buildAkkaS3Settings(
      awsClientConfig = AWSClientConfig(
        accessKey = Some(accessKey),
        secretKey = Some(secretKey),
        endpoint = Some(endpoint),
        maxConnections = None,
        region = "localhost"
      )
    )

    testWith(s3AkkaClient)
  }

  def withS3AkkaSettings[R](testWith: TestWith[S3Settings, R])(
    implicit actorSystem: ActorSystem): R =
    withS3AkkaSettings(endpoint = localS3EndpointUrl) { s3AkkaClient =>
      testWith(s3AkkaClient)
    }
}
