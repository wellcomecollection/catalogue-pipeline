package uk.ac.wellcome.models.work.internal

sealed trait Location {
  val accessConditions: List[AccessCondition]
}
object Location {
  case class OpenShelves(accessConditions: List[AccessCondition],
                         shelfmark: String,
                         shelfLocation: String)
      extends Location

  case class ClosedStores(accessConditions: List[AccessCondition])
      extends Location

  case class DigitalResource(accessConditions: List[AccessCondition],
                             url: String,
                             license: Option[License] = None,
                             credit: Option[String] = None,
                             format: Option[DigitalResourceFormat] = None)
      extends Location
}

sealed trait DigitalResourceFormat
object DigitalResourceFormat {

  case object IIIFPresentation extends DigitalResourceFormat

  case object IIIFImage extends DigitalResourceFormat

}
