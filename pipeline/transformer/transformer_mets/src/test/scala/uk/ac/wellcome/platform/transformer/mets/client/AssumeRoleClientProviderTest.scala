package uk.ac.wellcome.platform.transformer.mets.client

import com.amazonaws.services.s3.AmazonS3
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.transformer.mets.fixtures.{LocalStackS3Fixtures, STSFixtures}

class AssumeRoleClientProviderTest extends FunSpec with Akka with STSFixtures with Matchers with LocalStackS3Fixtures {
  it("gets temporary credentials that can be used to build a new client") {
    withActorSystem { implicit actorSystem =>
      withAssumeRoleClientProvider("arn:aws:iam::123456789012:role/new_role")(testS3ClientBuilder){ assumeRoleClientProvider =>
        val s3Client = assumeRoleClientProvider.getClient
        s3Client shouldBe a[Right[_,_]]
        s3Client.right.get shouldBe a[AmazonS3]
      }
    }
  }
}
