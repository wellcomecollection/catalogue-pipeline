package uk.ac.wellcome.mets_adapter.models

import uk.ac.wellcome.storage.ObjectLocation

/** METS location data to send onwards to the transformer.
  */
case class MetsLocation(bucket: String,
                        path: String,
                        version: Int,
                        file: String,
                        manifestations: List[String] = Nil) {

  def xmlLocation =
    ObjectLocation(bucket, s"${path}/${file}")

  def manifestationLocations =
    manifestations.map(file => ObjectLocation(bucket, s"${path}/${file}"))
}
