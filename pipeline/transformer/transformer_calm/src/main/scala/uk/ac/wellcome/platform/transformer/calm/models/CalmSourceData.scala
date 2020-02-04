package uk.ac.wellcome.platform.transformer.calm.models

case class CalmSourceData(recordId: String,
                          refNo: String,
                          altRefNo: Option[String],
                          title: Option[String])
