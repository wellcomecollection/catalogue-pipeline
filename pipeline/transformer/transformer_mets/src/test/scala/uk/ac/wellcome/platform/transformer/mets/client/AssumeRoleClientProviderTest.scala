package uk.ac.wellcome.platform.transformer.mets.client

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import org.scalatest.FunSpec
import uk.ac.wellcome.akka.fixtures.Akka

import scala.concurrent.ExecutionContext.Implicits._


class S3ClientBuilder(region: String, endpoint: String) extends ClientFactory[AmazonS3] {
  override def buildClient(credentials: BasicSessionCredentials): AmazonS3 =
    AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withEndpointConfiguration(new EndpointConfiguration(endpoint, region)).build()
}

class AssumeRoleClientProviderTest extends FunSpec with Akka {
  val accessKey = "testing"
  val secretKey = "testing"
  val endpoint = "http://localhost:5000"
  val region = "local"

  val stsClient = AWSSecurityTokenServiceClientBuilder.standard().withCredentials(
    new AWSStaticCredentialsProvider(
      new BasicAWSCredentials(accessKey, secretKey)))
    .withEndpointConfiguration(new EndpointConfiguration(endpoint, region))
    .build()

  it("gets temporary credentials that can be used to build a new client") {
    withActorSystem { implicit actorSystem =>
      new AssumeRoleClientProvider[AmazonS3](stsClient, "")(new S3ClientBuilder(endpoint, region))


    }
  }
}
