package uk.ac.wellcome.platform.transformer.calm.models

import uk.ac.wellcome.storage.ObjectLocation

/** Calm location data to send onwards to the transformer.
  */
case class CalmLocation(bucket: String,
                        path: String,
                        version: Int,
                        file: String,
                        manifestations: List[String] = Nil) {

  def xmlLocation =
    ObjectLocation(bucket, s"${path}/${file}")

  def manifestationLocations =
    manifestations.map(file => ObjectLocation(bucket, s"${path}/${file}"))
}
