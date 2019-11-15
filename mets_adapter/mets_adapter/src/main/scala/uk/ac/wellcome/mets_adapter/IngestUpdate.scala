package uk.ac.wellcome.mets_adapter.models

case class IngestUpdate(space: String, bagId: String)

case class MetsData(path: String, version: Int)

case class Bag(info: BagInfo, manifest: BagManifest, location: BagLocation, version: String) {

  // Storage-service only stores a list of files, so we need to search for a
  // XML file in data directory named with some b-number.
  private val metsRegex = "^data/b[0-9]{7}[0-9x].xml$".r

  private val versionRegex = "^v([0-9]+)".r

  def metsData: Either[Throwable, MetsData] =
    metsPath
      .map { path =>
        parsedVersion
          .map(version =>  Right(MetsData(path, version)))
          .getOrElse(Left(new Exception("Couldn't parse version")))
      }
      .getOrElse(Left(new Exception("Couldn't parse METS path")))

  def metsPath: Option[String] =
    manifest.files
      .collectFirst {
        case file if metsRegex.findFirstIn(file.name).nonEmpty =>
          s"${location.path}/${file.path}"
      }

  def parsedVersion: Option[Int] =
    version match {
      case versionRegex(num) => Some(num.toInt)
      case _ => None
    }
}

case class BagInfo(externalIdentifier: String)

case class BagManifest(files: List[BagFile])

case class BagLocation(bucket: String, path: String)

case class BagFile(name: String, path: String)
