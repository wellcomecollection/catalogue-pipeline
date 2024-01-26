package weco.catalogue.source_model.sierra.rules

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocationType}
import weco.sierra.models.identifiers.TypedSierraRecordNumber

object SierraPhysicalLocationType extends Logging {
  def fromName(
    id: TypedSierraRecordNumber,
    name: String
  ): Option[PhysicalLocationType] =
    name.toLowerCase match {
      case lowerCaseName
          if lowerCaseName.hasSubstring(
            "archives & mss well.coll",
            "at digitisation",
            "by appointment",
            "closed stores",
            "conservation",
            "early printed books",
            "iconographic collection",
            "offsite",
            "unrequestable"
          ) =>
        Some(LocationType.ClosedStores)

      case lowerCaseName
          if lowerCaseName.hasSubstring(
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
          ) =>
        Some(LocationType.OpenShelves)

      case lowerCaseName
          if lowerCaseName.hasSubstring(
            "exhibition"
          ) =>
        Some(LocationType.OnExhibition)

      case lowerCaseName if lowerCaseName == "" || lowerCaseName == "none" =>
        None

      case _ =>
        if (!knownUnknownLocations.contains(name)) {
          warn(
            s"${id.withCheckDigit}: Unable to map Sierra location name to LocationType: $name"
          )
        }
        None
    }

  // This is a list of location names that we know can't be mapped to a location type,
  // for which we don't log a warning to avoid cluttering up logs.
  //
  // https://en.wikipedia.org/wiki/There_are_known_knowns
  private val knownUnknownLocations = Set(
    "bound in above",
    "Contained in above"
  )

  implicit class StringOps(s: String) {
    def hasSubstring(substrings: String*): Boolean =
      substrings.exists { s.contains }
  }
}
