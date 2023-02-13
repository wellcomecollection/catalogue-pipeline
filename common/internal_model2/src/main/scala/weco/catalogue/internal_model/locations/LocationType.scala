package weco.catalogue.internal_model.locations

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}

sealed trait LocationType extends EnumEntry {
  val id: String
  val label: String

  override lazy val entryName: String = id
}

sealed trait PhysicalLocationType extends LocationType
sealed trait DigitalLocationType extends LocationType

object LocationType extends Enum[LocationType] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for LocationType are not unique!"
  )

  implicit val locationTypeEncoder: Encoder[LocationType] =
    Encoder.forProduct1("id")(_.id)

  implicit val physicalLocationTypeEncoder: Encoder[PhysicalLocationType] =
    locType => locationTypeEncoder.apply(locType)

  implicit val digitalLocationTypeEncoder: Encoder[DigitalLocationType] =
    locType => locationTypeEncoder.apply(locType)

  implicit val locationTypeDecoder: Decoder[LocationType] =
    Decoder.forProduct1("id")(LocationType.withName)

  implicit val physicalLocationTypeDecoder: Decoder[PhysicalLocationType] =
    cursor =>
      locationTypeDecoder.apply(cursor).map {
        _.asInstanceOf[PhysicalLocationType]
      }

  implicit val digitalLocationTypeDecoder: Decoder[DigitalLocationType] =
    cursor =>
      locationTypeDecoder.apply(cursor).map {
        _.asInstanceOf[DigitalLocationType]
      }

  case object ClosedStores extends PhysicalLocationType {
    val id = "closed-stores"
    val label = "Closed stores"
  }

  case object OpenShelves extends PhysicalLocationType {
    val id = "open-shelves"
    val label = "Open shelves"
  }

  case object OnExhibition extends PhysicalLocationType {
    val id = "on-exhibition"
    val label = "On exhibition"
  }

  case object OnOrder extends PhysicalLocationType {
    val id = "on-order"
    val label = "On order"
  }

  case object IIIFPresentationAPI extends DigitalLocationType {
    val id = "iiif-presentation"
    val label = "IIIF Presentation API"
  }

  case object IIIFImageAPI extends DigitalLocationType {
    val id = "iiif-image"
    val label = "IIIF Image API"
  }

  case object ThumbnailImage extends DigitalLocationType {
    val id = "thumbnail-image"
    val label = "Thumbnail image"
  }

  case object OnlineResource extends DigitalLocationType {
    val id = "online-resource"
    val label = "Online resource"
  }
}
