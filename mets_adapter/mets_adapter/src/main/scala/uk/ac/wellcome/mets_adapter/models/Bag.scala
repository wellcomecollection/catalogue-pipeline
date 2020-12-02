package uk.ac.wellcome.mets_adapter.models

import java.time.Instant

/** The response receiveved from the storage-service bag API.
  */
case class Bag(info: BagInfo,
               manifest: BagManifest,
               location: BagLocation,
               version: String,
               createdDate: Instant) {

  def metsSourceData: Either[Exception, MetsSourceData] =
    metsFile
      .flatMap { metsFile =>
        parsedVersion.map { version =>
          MetsSourceData(
            bucket = location.bucket,
            path = location.path,
            version = version,
            file = metsFile,
            createdDate = createdDate,
            // If the only file in the bag is the METS file, that means
            // the bag has been deleted. Check https://github.com/wellcomecollection/platform/issues/4893
            deleted = containsOnlyMetsFile(metsFile),
            manifestations = manifestations
          )
        }
      }

  private def containsOnlyMetsFile(metsFile: String) = {
    manifest.files.forall(f => f.path == metsFile)
  }

  // Storage-service only stores a list of files, so we need to search for a
  // XML file in data directory named with some b-number.
  private val metsFileRegex = "^data/b[0-9]{7}[0-9x].xml$".r

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
