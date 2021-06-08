package weco.catalogue.internal_model.work

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  DigitalLocation,
  Location,
  LocationType,
  PhysicalLocation
}

sealed trait Availability extends EnumEntry {
  val id: String
  val label: String

  override lazy val entryName: String = id
}

object Availability extends Enum[Availability] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for Availability are not unique!"
  )

  implicit val availabilityEncoder: Encoder[Availability] =
    Encoder.forProduct1("id")(_.id)

  implicit val availabilityDecoder: Decoder[Availability] =
    Decoder.forProduct1("id")(Availability.withName)

  case object Online extends Availability {
    val id = "online"
    val label = "Online"
  }

  case object InLibrary extends Availability {
    val id = "in-library"
    val label = "In the library"
  }
}

object Availabilities {
  def forWorkData(data: WorkData[_]): Set[Availability] = {
    val locations =
      data.items.flatMap { _.locations } ++
        data.holdings.flatMap { _.location }

    Set(
      when(locations.exists(_.isAvailableInLibrary)) {
        Availability.InLibrary
      },
      when(locations.exists(_.isAvailableOnline)) {
        Availability.Online
      },
    ).flatten
  }

  private implicit class LocationOps(loc: Location) {
    def isAvailableInLibrary: Boolean =
      loc match {
        // The availability filter is meant to show people things they
        // can actually see, so we don't include items that have been ordered
        // but aren't available to view yet.
        case physicalLoc: PhysicalLocation
          if physicalLoc.locationType == LocationType.OnOrder => false

        // Don't include items if they have a physical location but are actually
        // held in another institution.
        //
        // Right now we do crude string matching on the terms in the access condition.
        // There might be something better we can do that looks at the reference numbers,
        // but for now this is a significant improvement without much effort.
        //
        // See https://github.com/wellcomecollection/platform/issues/5190
        case physicalLoc: PhysicalLocation
          if physicalLoc.accessConditions.exists { _.termsAreOtherInstitution } => false

        case _: PhysicalLocation => true

        case _ => false
      }

    def isAvailableOnline: Boolean =
      loc match {
        case digitalLoc: DigitalLocation if digitalLoc.isAvailable => true
        case _                                                     => false
      }
  }

  private implicit class OptionalAccessConditionOps(ac: AccessCondition) {
    def termsAreOtherInstitution: Boolean =
      ac.terms match {
        case Some(t) if t.toLowerCase.contains("available at") => true
        case Some(t) if t.toLowerCase.contains("available by appointment at") => true

        case Some(t) if t.contains("Churchill Archives Centre") => true
        case Some(t) if t.contains("UCL Special Collections and Archives") => true
        case Some(t) if t.contains("at King's College London") => true
        case Some(t) if t.contains("at the Army Medical Services Museum") => true
        case Some(t) if t.contains("currently remains with the Martin Leake family") => true

        case _ => false
      }
  }

  private def when[T](condition: Boolean)(result: T): Option[T] =
    if (condition) Some(result) else { None }
}
