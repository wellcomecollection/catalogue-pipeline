package uk.ac.wellcome.mets_adapter.models

/** METS data to send onwards to the transformer.
  */
case class MetsData(bucket: String,
                    path: String,
                    version: Int,
                    file: String,
                    manifestations: List[String] = Nil)
