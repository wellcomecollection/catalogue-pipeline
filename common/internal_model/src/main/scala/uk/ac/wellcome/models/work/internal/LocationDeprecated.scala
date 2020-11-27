package uk.ac.wellcome.models.work.internal

sealed trait LocationDeprecated {
  val locationType: LocationType
  val accessConditions: List[AccessCondition]

  def hasRestrictions: Boolean =
    accessConditions.exists(_.hasRestrictions)
}

case class DigitalLocationDeprecated(
  url: String,
  locationType: LocationType,
  license: Option[License] = None,
  credit: Option[String] = None,
  accessConditions: List[AccessCondition] = Nil
) extends LocationDeprecated

case class PhysicalLocationDeprecated(
  locationType: LocationType,
  label: String,
  accessConditions: List[AccessCondition] = Nil
) extends LocationDeprecated
