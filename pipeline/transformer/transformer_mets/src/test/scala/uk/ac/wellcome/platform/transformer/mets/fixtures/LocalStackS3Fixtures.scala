package uk.ac.wellcome.platform.transformer.mets.fixtures

import com.amazonaws.services.s3.AmazonS3
import org.scalatest.Suite
import uk.ac.wellcome.fixtures.{Fixture, fixture, safeCleanup}
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3ClientFactory
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec

import scala.collection.JavaConverters._

trait LocalStackS3Fixtures extends S3Fixtures { this: Suite =>
  val localStackS3Endoint = "http://localhost:4572"
  val localStackS3Client: AmazonS3 = S3ClientFactory.create(
    region = "localhost",
    endpoint = localStackS3Endoint,
    accessKey = "",
    secretKey = ""
  )
  val localStackS3Store = S3TypedStore[String](implicitly[Codec[String]], localStackS3Client)
  val testS3ClientBuilder = new TestS3ClientBuilder(localStackS3Endoint, "us-east-1")

  def withLocalStackS3Bucket[R]: Fixture[Bucket, R] =
    fixture[Bucket, R](
      create = {
        eventually {
          localStackS3Client.listBuckets().asScala.size should be >= 0
        }
        val bucketName: String = createBucketName
        localStackS3Client.createBucket(bucketName)
        eventually { localStackS3Client.doesBucketExistV2(bucketName) }

        Bucket(bucketName)
      },
      destroy = { bucket: Bucket =>
        if (localStackS3Client.doesBucketExistV2(bucket.name)) {

          listKeysInBucket(bucket).foreach { key =>
            safeCleanup(key) {
              localStackS3Client.deleteObject(bucket.name, _)
            }
          }

          localStackS3Client.deleteBucket(bucket.name)
        } else {
          info(s"Trying to clean up ${bucket.name}, bucket does not exist.")
        }
      }
    )
}
