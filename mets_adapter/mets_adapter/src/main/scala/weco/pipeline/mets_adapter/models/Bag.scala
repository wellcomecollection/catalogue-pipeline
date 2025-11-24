package weco.pipeline.mets_adapter.models

import weco.storage.providers.s3.S3ObjectLocationPrefix

import java.time.Instant
import weco.catalogue.source_model.mets.{
  DeletedMetsFile,
  MetsFileWithImages,
  MetsSourceData
}

/** The response received from the storage-service bag API.
  */
case class Bag(
  info: BagInfo,
  manifest: BagManifest,
  location: BagLocation,
  version: String,
  createdDate: Instant
) {

  def metsSourceData: Either[Throwable, MetsSourceData] =
    parsedVersion.flatMap {
      version =>
        // If the bag doesn't contain any files, then it's empty and the
        // METS file has been deleted.
        // See https://github.com/wellcomecollection/platform/issues/4872
        if (manifest.files.isEmpty) {
          Right(
            DeletedMetsFile(
              modifiedTime = createdDate,
              version = version
            )
          )
        } else {

          metsFile.map {
            filename =>
              // If the only file in the bag is the METS file, that means
              // the bag has been deleted.
              // See https://github.com/wellcomecollection/platform/issues/4893
              if (containsOnlyMetsFile(filename)) {
                DeletedMetsFile(
                  modifiedTime = createdDate,
                  version = version
                )
              } else {
                MetsFileWithImages(
                  root = S3ObjectLocationPrefix(
                    bucket = location.bucket,
                    keyPrefix = location.path
                  ),
                  filename = filename,
                  manifestations = manifestations,
                  modifiedTime = createdDate,
                  version = version
                )
              }
          }
        }
    }

  private def containsOnlyMetsFile(metsFile: String): Boolean =
    manifest.files.forall(f => f.path == metsFile)

  // Storage-service only stores a list of files, so we need to search for a
  // XML file in data directory named with some b-number.
  private val metsFileRegex =
    "^data/(b[0-9]{7}[0-9x]|METS\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}).xml$".r

  // A bag can contain number of manifestations, generally named the same as the
  // main METS file followed by an underscore and it's (zero-padded) index.
  private val manifestationRegex = "^data/b[0-9]{7}[0-9x]_\\w+.xml$".r

  private val versionRegex = "^v([0-9]+)".r

  def metsFile: Either[Exception, String] =
    manifest.files
      .collectFirst {
        case file if metsFileRegex.findFirstIn(file.name).nonEmpty =>
          Right(file.path)
      }
      .getOrElse(Left(new Exception("Couldn't find METS file")))

  def manifestations: List[String] =
    manifest.files
      .collect {
        case file if manifestationRegex.findFirstIn(file.name).nonEmpty =>
          file.path
      }

  def parsedVersion: Either[Exception, Int] =
    version match {
      case versionRegex(num) => Right(num.toInt)
      case _                 => Left(new Exception("Couldn't parse version"))
    }
}

case class BagInfo(externalIdentifier: String)

case class BagManifest(files: List[BagFile])

case class BagLocation(bucket: String, path: String)

case class BagFile(name: String, path: String)
