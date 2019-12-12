package uk.ac.wellcome.platform.transformer.mets.fixtures

import com.amazonaws.auth.{
  AWSStaticCredentialsProvider,
  BasicSessionCredentials
}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import uk.ac.wellcome.platform.transformer.mets.client.ClientFactory

class TestS3ClientBuilder(endpoint: String, region: String)
    extends ClientFactory[AmazonS3] {
  override def buildClient(credentials: BasicSessionCredentials): AmazonS3 =
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(new EndpointConfiguration(endpoint, region))
      .build()
}
