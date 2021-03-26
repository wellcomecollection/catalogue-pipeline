package weco.catalogue.internal_model.work

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  Location,
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
      data.items.flatMap { _.locations} ++
        data.holdings.flatMap { _.location }

    Set(
      when(locations.exists(_.isPhysicalLocation)) {
        Availability.InLibrary
      },
      when(locations.exists(_.isAvailableOnline)) {
        Availability.Online
      },
    ).flatten
  }

  private implicit class LocationOps(loc: Location) {
    def isPhysicalLocation: Boolean =
      loc.isInstanceOf[PhysicalLocation]

    def isAvailableOnline: Boolean =
      loc match {
        case digitalLoc: DigitalLocation if digitalLoc.isAvailable => true
        case _ => false
      }
  }

  private def when[T](condition: Boolean)(result: T): Option[T] =
    if (condition) Some(result) else { None }
}
