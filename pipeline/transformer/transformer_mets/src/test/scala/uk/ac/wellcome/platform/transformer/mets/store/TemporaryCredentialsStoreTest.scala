package uk.ac.wellcome.platform.transformer.mets.store

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.transformer.mets.fixtures.{
  LocalStackS3Fixtures,
  STSFixtures
}
import uk.ac.wellcome.storage.Identified

import scala.util.Right

class TemporaryCredentialsStoreTest
    extends AnyFunSpec
    with LocalStackS3Fixtures
    with STSFixtures
    with Akka {
  val roleArn = "arn:aws:iam::123456789012:role/new_role"

  it("gets a file from s3 using temporary credentials") {
    withActorSystem { implicit actorSystem =>
      withLocalStackS3Bucket { bucket =>
        val location = createS3ObjectLocationWith(bucket)
        val content = "Rudolph the red node reindeer"
        localStackS3Store.put(location)(content)
        withAssumeRoleClientProvider(roleArn)(testS3ClientBuilder) {
          assumeRoleClientProvider =>
            val result = new TemporaryCredentialsStore[String](
              assumeRoleClientProvider).get(location)
            result shouldBe a[Right[_, _]]
            result.right.get shouldBe Identified(location, content)
        }
      }
    }
  }
}
