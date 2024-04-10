package weco.catalogue.source_model.sierra.rules

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.{
  LocationType,
  PhysicalLocationType
}
import weco.sierra.models.fields.SierraLocation
import weco.sierra.models.identifiers.TypedSierraRecordNumber

object SierraPhysicalLocationType extends Logging {
  private val closedStores = Seq(
    "archives & mss well.coll",
    "at digitisation",
    "by appointment",
    "closed stores",
    "conservation",
    "early printed books",
    "iconographic collection",
    "offsite",
    "unrequestable"
  )

  private val openShelves = Seq(
    "biographies",
    "folios",
    "history of medicine",
    "journals",
    "medical collection",
    "medicine & society collection",
    "open shelves",
    "quick ref collection",
    "quick ref. collection",
    "rare materials room",
    "student coll"
  )

  private val onExhibition = Seq("exhibition")
  // This is a list of location names that we know can't be mapped to a location type,
  // for which we don't log a warning to avoid cluttering up logs.
  //
  // https://en.wikipedia.org/wiki/There_are_known_knowns
  private val knownUnknownLocations = Set(
    "bound in above",
    "contained in above"
  )

  def fromLocation(
    id: TypedSierraRecordNumber,
    sierraLocation: SierraLocation
  ): Option[PhysicalLocationType] = {
    sierraLocation.copy(name = sierraLocation.name.toLowerCase()) match {
      case SierraLocation("harop", _) | SierraLocation("harcl", _) =>
        Some(LocationType.Offsite)
      case SierraLocation(_, lcName) if lcName.hasSubstring(closedStores: _*) =>
        Some(LocationType.ClosedStores)
      case SierraLocation(_, lcName) if lcName.hasSubstring(openShelves: _*) =>
        Some(LocationType.OpenShelves)
      case SierraLocation(_, lcName) if lcName.hasSubstring(onExhibition: _*) =>
        Some(LocationType.OnExhibition)
      case SierraLocation(_, "") | SierraLocation(_, "none") => None
      case SierraLocation(_, lcName)
          if knownUnknownLocations.contains(lcName) =>
        None
      case unknownLocation =>
        warn(
          s"${id.withCheckDigit}: Unable to map Sierra location name to LocationType: $unknownLocation"
        )
        None
    }
  }

  implicit class StringOps(s: String) {
    def hasSubstring(substrings: String*): Boolean =
      substrings.exists { s.contains }
  }
}
