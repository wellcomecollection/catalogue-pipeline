package weco.catalogue.source_model.mets

import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.Instant

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
