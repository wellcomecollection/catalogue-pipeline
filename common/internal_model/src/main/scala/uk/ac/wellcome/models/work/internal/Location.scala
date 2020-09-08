package uk.ac.wellcome.models.work.internal

sealed trait Location {
  val accessConditions: List[AccessCondition]
}
object Location {
  case class OpenShelves(accessConditions: List[AccessCondition],
                         shelfmark: String,
                         sectionName: String)
      extends Location
  case class ClosedStores(accessConditions: List[AccessCondition])
      extends Location
  case class DigitalResource(accessConditions: List[AccessCondition],
                             source: DigitalSource)
      extends Location
}

sealed trait DigitalSource {
  val url: String
  val license: Option[License]
  val credit: Option[String]
}

object DigitalSource {
  case class IIIFPresentation(url: String,
                              license: Option[License],
                              credit: Option[String])
      extends DigitalSource
  case class IIIFImage(url: String,
                       license: Option[License],
                       credit: Option[String])
      extends DigitalSource
  case class ExternalSource(url: String,
                            license: Option[License],
                            credit: Option[String])
      extends DigitalSource
}
