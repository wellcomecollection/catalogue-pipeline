package uk.ac.wellcome.platform.transformer.calm.fixtures

import akka.actor.ActorSystem
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.calm.client.{
  AssumeRoleClientProvider,
  ClientFactory
}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

trait STSFixtures {
  val localstackAccessKey = "test"
  val localStackSecretKey = "test"
  val stsEndpoint = "http://localhost:4592"
  val region = "us-east-1"

  val stsClient = AWSSecurityTokenServiceClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(
      new BasicAWSCredentials(localstackAccessKey, localStackSecretKey)))
    .withEndpointConfiguration(new EndpointConfiguration(stsEndpoint, region))
    .build()

  def withAssumeRoleClientProvider[R, T](
    roleArn: String,
    interval: FiniteDuration = 30 seconds)(clientFactory: ClientFactory[T])(
    testWith: TestWith[AssumeRoleClientProvider[T], R])(
    implicit actorSystem: ActorSystem) = {
    testWith(
      new AssumeRoleClientProvider[T](stsClient, roleArn, interval)(
        clientFactory))
  }
}
