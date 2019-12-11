package uk.ac.wellcome.platform.transformer.mets.client

import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.AmazonS3
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.mets.fixtures.{LocalStackS3Fixtures, STSFixtures}

import scala.collection.mutable
import scala.concurrent.duration._

class AssumeRoleClientProviderTest extends FunSpec with Akka with STSFixtures with Matchers with LocalStackS3Fixtures with MockitoSugar{

  it("return a client using temporary credentials") {
    withActorSystem { implicit actorSystem =>
      withMockClientFactory(testS3ClientBuilder) { case (mockClientFactory, invocationArguments) =>
        withAssumeRoleClientProvider("arn:aws:iam::123456789012:role/new_role", 1 minute)(mockClientFactory) { assumeRoleClientProvider =>
          val s3Client1 = assumeRoleClientProvider.getClient

          s3Client1 shouldBe a[Right[_, _]]
          s3Client1.right.get shouldBe a[AmazonS3]
          val s3Client2 = assumeRoleClientProvider.getClient
          Thread.sleep((15 milliseconds).toMillis)
          val s3Client3= assumeRoleClientProvider.getClient

          s3Client2.right.get should be theSameInstanceAs(s3Client3.right.get)

          invocationArguments.length should be >=1
          invocationArguments.length should be <= 2
        }
      }
    }
  }

  it("refreshes the credentials with the interval specified") {
    withActorSystem { implicit actorSystem =>
      withMockClientFactory(testS3ClientBuilder) { case (mockClientFactory, credentials) =>
        withAssumeRoleClientProvider("arn:aws:iam::123456789012:role/new_role", 10 milliseconds)(mockClientFactory) { assumeRoleClientProvider =>
          val s3Client1 = assumeRoleClientProvider.getClient
          Thread.sleep((15 milliseconds).toMillis)
          val s3Client2 = assumeRoleClientProvider.getClient

          s3Client1.right.get shouldNot be theSameInstanceAs(s3Client2.right.get)

          credentials.length should be >= 2
          credentials.head.getSessionToken shouldNot be (credentials.tail.head.getSessionToken)
        }
      }
    }
  }

  def withMockClientFactory[T,R](clientFactory: ClientFactory[T])(testWith: TestWith[(ClientFactory[T], mutable.MutableList[BasicSessionCredentials]),R]) = {
    val mockClientFactory = mock[ClientFactory[T]]
    val credentialsAccumulator = new mutable.MutableList[BasicSessionCredentials]
    Mockito.when(mockClientFactory.buildClient(org.mockito.Matchers.any[BasicSessionCredentials])).thenAnswer(new Answer[T] {
      override def answer(invocation: InvocationOnMock): T = {
        val arguments = invocation.getArguments
        val credentials = arguments.head.asInstanceOf[BasicSessionCredentials]
        credentialsAccumulator += credentials
        clientFactory.buildClient(credentials)
      }
    })
    testWith((mockClientFactory, credentialsAccumulator))
  }
}
