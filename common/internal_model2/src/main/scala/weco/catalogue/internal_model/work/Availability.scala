package weco.catalogue.internal_model.work

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}

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

  case object ClosedStores extends Availability {
    val id = "closed-stores"
    val label = "Closed stores"
  }

  case object OpenShelves extends Availability {
    val id = "open-shelves"
    val label = "Open shelves"
  }
}
