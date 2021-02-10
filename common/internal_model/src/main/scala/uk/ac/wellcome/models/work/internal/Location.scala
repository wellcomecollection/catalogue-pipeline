package uk.ac.wellcome.models.work.internal

sealed trait Location {
  val locationType: LocationType
  val accessConditions: List[AccessCondition]
  val license: Option[License]

  def hasRestrictions: Boolean =
    accessConditions.exists(_.hasRestrictions)
}

case class DigitalLocation(
  url: String,
  locationType: DigitalLocationType,
  license: Option[License] = None,
  credit: Option[String] = None,
  linkText: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends Location

case class PhysicalLocation(
  locationType: PhysicalLocationType,
  label: String,
  license: Option[License] = None,
  shelfmark: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends Location {

  if (locationType == LocationType.ClosedStores) {
    require(
      label == LocationType.ClosedStores.label,
      s"Label is '$label', but we don't want to expose the layout of our Closed Stores"
    )
  }
}
