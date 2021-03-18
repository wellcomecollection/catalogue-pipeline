package uk.ac.wellcome.models.work.internal

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
  def forWorkData(data: WorkData[_]): Set[Availability] =
    Set(
      when(containsLocation(_.isInstanceOf[PhysicalLocation])(data.items))(
        Availability.InLibrary
      ),
      when(isAvailableOnline(data.items))(
        Availability.Online
      )
    ).flatten

  private def isAvailableOnline: List[Item[_]] => Boolean =
    containsLocation {
      case location: DigitalLocation if location.isAvailable => true
      case _                                                 => false
    }

  private def containsLocation(predicate: Location => Boolean)(
    items: List[Item[_]]): Boolean =
    items.exists { item =>
      item.locations.exists(predicate)
    }

  private def when[T](condition: => Boolean)(property: T): Option[T] =
    if (condition) Some(property) else { None }
}
