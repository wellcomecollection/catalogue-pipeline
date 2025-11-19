package weco.catalogue.source_model.mets

import weco.storage.providers.s3.{S3ObjectLocation, S3ObjectLocationPrefix}

import java.time.Instant

sealed trait MetsSourceData {
  val modifiedTime: Instant
  val version: Int
}

case class MetsFileWithImages(
  root: S3ObjectLocationPrefix,
  filename: String,
  manifestations: List[String],
  modifiedTime: Instant,
  version: Int
) extends MetsSourceData {

  def xmlLocation: S3ObjectLocation =
    root.asLocation(filename)

  def manifestationLocations: List[S3ObjectLocation] =
    manifestations.map { root.asLocation(_) }

  // We store these values in DynamoDB, which only supports second-level
  // precision of Instant values.  We can treat two instances of this class
  // as equal if their createdDates are at the same second.
  override def equals(other: Any): Boolean =
    other match {
      case m: MetsFileWithImages
          if m.root == root &&
            m.filename == filename &&
            m.manifestations == manifestations &&
            m.modifiedTime.getEpochSecond == modifiedTime.getEpochSecond &&
            m.version == version =>
        true
      case _ => false
    }
}

case class DeletedMetsFile(
  modifiedTime: Instant,
  version: Int
) extends MetsSourceData {

  // We store these values in DynamoDB, which only supports second-level
  // precision of Instant values.  We can treat two instances of this class
  // as equal if their createdDates are at the same second.
  override def equals(other: Any): Boolean =
    other match {
      case d: DeletedMetsFile
          if d.modifiedTime.getEpochSecond == modifiedTime.getEpochSecond &&
            d.version == version =>
        true
      case _ => false
    }
}
