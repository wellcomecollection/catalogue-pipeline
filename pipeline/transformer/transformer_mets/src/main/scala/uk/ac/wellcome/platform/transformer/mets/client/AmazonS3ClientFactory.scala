package uk.ac.wellcome.platform.transformer.mets.client
import com.amazonaws.auth.{
  AWSStaticCredentialsProvider,
  BasicSessionCredentials
}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import uk.ac.wellcome.config.models.AWSClientConfig

trait ClientFactory[T] {
  def buildClient(credentials: BasicSessionCredentials): T
}

class AmazonS3ClientFactory(config: AWSClientConfig)
    extends ClientFactory[AmazonS3] {
  override def buildClient(credentials: BasicSessionCredentials): AmazonS3 =
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withRegion(config.region)
      .build()
}
