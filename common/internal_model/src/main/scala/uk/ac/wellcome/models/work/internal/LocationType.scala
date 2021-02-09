package uk.ac.wellcome.models.work.internal

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder, Json}

sealed trait LocationType extends EnumEntry {
  val id: String
  val label: String
}

sealed trait PhysicalLocationType extends LocationType
sealed trait DigitalLocationType extends LocationType

object LocationType extends Enum[LocationType] {
  val values = findValues

  implicit val locationTypeEncoder: Encoder[LocationType] =
    Encoder.instance[LocationType] { locationType =>
      Json.obj(
        ("id", Json.fromString(locationType.id))
      )
    }

  implicit val physicalLocationTypeEncoder: Encoder[PhysicalLocationType] =
    locType => locationTypeEncoder.apply(locType)

  implicit val digitalLocationTypeEncoder: Encoder[DigitalLocationType] =
    locType => locationTypeEncoder.apply(locType)

  private val locationTypeIdMap = {
    val idPairs = values.map { locationType =>
      locationType.id -> locationType
    }

    // Check we don't have any duplicate IDs
    assert(idPairs.toMap.size == idPairs.size)

    idPairs.toMap
  }

  implicit val locationTypeDecoder: Decoder[LocationType] =
    Decoder.instance[LocationType] { cursor =>
      for {
        id <- cursor.downField("id").as[String]
      } yield {
        locationTypeIdMap.getOrElse(
          id,
          throw new Exception(s"$id is not a valid LocationType ID")
        )
      }
    }

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
    val label = "Closed Stores"
  }

  case object OpenShelves extends PhysicalLocationType {
    val id = "open-shelves"
    val label = "Open Shelves"
  }

  case object OnExhibition extends PhysicalLocationType {
    val id = "on-exhibition"
    val label = "On Exhibition"
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
