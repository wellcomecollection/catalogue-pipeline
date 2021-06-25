package weco.catalogue.source_model.mets

import weco.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}

import java.time.Instant

sealed trait MetsSourceData {
  val createdDate: Instant
  val version: Int
}

case class MetsFileWithImages(
  root: S3ObjectLocationPrefix,
  filename: String,
  manifestations: List[String],
  createdDate: Instant,
  version: Int
) extends MetsSourceData {

  def xmlLocation: S3ObjectLocation =
    root.asLocation(filename)

  def manifestationLocations: List[S3ObjectLocation] =
    manifestations.map { root.asLocation(_) }
}

case class DeletedMetsFile(
  createdDate: Instant,
  version: Int
) extends MetsSourceData
