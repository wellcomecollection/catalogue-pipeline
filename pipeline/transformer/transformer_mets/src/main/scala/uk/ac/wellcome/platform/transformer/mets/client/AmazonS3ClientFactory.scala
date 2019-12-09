package uk.ac.wellcome.platform.transformer.mets.client
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import uk.ac.wellcome.config.models.AWSClientConfig

class AmazonS3ClientFactory(config: AWSClientConfig) extends ClientFactory[AmazonS3] {
  override def buildClient(credentials: BasicSessionCredentials): AmazonS3=
    AmazonS3ClientBuilder.standard().withRegion(config.region).build()
}
