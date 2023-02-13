package weco.catalogue.internal_model.work

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import weco.catalogue.internal_model.locations.{
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

  case object ClosedStores extends Availability {
    val id = "closed-stores"
    val label = "Closed stores"
  }

  case object OpenShelves extends Availability {
    val id = "open-shelves"
    val label = "Open shelves"
  }
}

object Availabilities {
  def forWorkData(data: WorkData[_]): Set[Availability] =
    (data.items.flatMap(_.locations) ++ data.holdings.flatMap(_.location))
      .flatMap(_.availability(data.notes))
      .toSet

  private implicit class LocationOps(loc: Location) {
    def availability(notes: Seq[Note]): Option[Availability] =
      loc match {
        case PhysicalLocation(LocationType.OpenShelves, _, _, _, _) =>
          Some(Availability.OpenShelves)
        case PhysicalLocation(LocationType.ClosedStores, _, _, _, _)
            if !notes.exists(_.isInOtherLibrary) =>
          Some(Availability.ClosedStores)
        case digitalLocation: DigitalLocation if digitalLocation.isAvailable =>
          Some(Availability.Online)
        case _ => None
      }
  }

  private implicit class NoteOps(n: Note) {
    // Don't include items if they have a physical location but are actually
    // held in another institution.
    //
    // Right now we do crude string matching on the terms in the access condition.
    // There might be something better we can do that looks at the reference numbers,
    // but for now this is a significant improvement without much effort.
    //
    // See https://github.com/wellcomecollection/platform/issues/5190
    def isInOtherLibrary: Boolean =
      n match {
        case Note.TermsOfUse(terms) => termsAreOtherInstitution(terms)
        case _                      => false
      }

    def termsAreOtherInstitution(terms: String): Boolean =
      terms match {
        case t if t.toLowerCase.contains("available at") => true
        case t if t.toLowerCase.contains("available by appointment at") =>
          true

        case t if t.contains("Churchill Archives Centre") => true
        case t if t.contains("UCL Special Collections and Archives") =>
          true
        case t if t.contains("at King's College London") => true
        case t if t.contains("at the Army Medical Services Museum") =>
          true
        case t
            if t.contains("currently remains with the Martin Leake family") =>
          true

        case _ => false
      }
  }
}
