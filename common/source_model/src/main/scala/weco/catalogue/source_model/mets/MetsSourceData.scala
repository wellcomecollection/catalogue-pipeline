package weco.catalogue.source_model.mets

import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}

import java.time.Instant

sealed trait NewMetsSourceData {
  val createdDate: Instant
  val version: Int
}

case class MetsFileWithImages(
  root: S3ObjectLocationPrefix,
  filename: String,
  manifestations: List[String],
  createdDate: Instant,
  version: Int
) extends NewMetsSourceData {

  def xmlLocation: S3ObjectLocation =
    root.asLocation(filename)

  def manifestationLocations: List[S3ObjectLocation] =
    manifestations.map { root.asLocation(_) }
}

case class DeletedMetsFile(
  createdDate: Instant,
  version: Int
) extends NewMetsSourceData

/** METS location data to send onwards to the transformer.
  */
case class MetsSourceData(bucket: String,
                          path: String,
                          version: Int,
                          file: String,
                          createdDate: Instant,
                          deleted: Boolean,
                          manifestations: List[String] = Nil) {

  def xmlLocation: S3ObjectLocation =
    S3ObjectLocation(bucket, key = s"$path/$file")

  def manifestationLocations: List[S3ObjectLocation] =
    manifestations.map(mf => S3ObjectLocation(bucket, key = s"$path/$mf"))
}
