package uk.ac.wellcome.mets_adapter.models

case class IngestUpdate(space: String, bagId: String)

case class MetsData(path: String, version: Int)

case class Bag(info: BagInfo, manifest: BagManifest, location: BagLocation) {

  // Storage-service only stores a list of files, so we need to search for a
  // XML file in data directory named with some b-number.
  private val metsRegex = "^data/b[0-9]{7}[0-9x].xml$".r

  def metsData: Either[Throwable, MetsData] =
    Left(new Exception("Not found"))

  def metsPath: Option[String] =
    manifest.files
      .collectFirst {
        case file if metsRegex.findFirstIn(file.name).nonEmpty =>
          s"${location.path}/${file.path}"
      }
}

case class BagInfo(externalIdentifier: String)

case class BagManifest(files: List[BagFile])

case class BagLocation(bucket: String, path: String)

case class BagFile(name: String, path: String)
