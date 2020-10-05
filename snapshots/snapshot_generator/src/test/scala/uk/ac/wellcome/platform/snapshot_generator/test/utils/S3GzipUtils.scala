package uk.ac.wellcome.platform.snapshot_generator.test.utils

import java.io.File

import com.amazonaws.services.s3.model.GetObjectRequest
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.s3.S3ObjectLocation

trait S3GzipUtils extends GzipUtils with S3Fixtures {
  def getGzipObjectFromS3(location: S3ObjectLocation): String = {
    val downloadFile =
      File.createTempFile("snapshotServiceTest", ".txt.gz")
    s3Client.getObject(
      new GetObjectRequest(location.bucket, location.key),
      downloadFile
    )

    readGzipFile(downloadFile.getPath)
  }
}
