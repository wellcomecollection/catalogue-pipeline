package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.{
  NewLocationType,
  PhysicalLocationType
}

object SierraPhysicalLocationType extends Logging {
  def fromName(name: String): Option[PhysicalLocationType] =
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
        Some(NewLocationType.ClosedStores)

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
        Some(NewLocationType.OpenShelves)

      case lowerCaseName
          if lowerCaseName.hasSubstring(
            "exhibition"
          ) =>
        Some(NewLocationType.OnExhibition)

      case lowerCaseName if lowerCaseName == "" || lowerCaseName == "none" =>
        None

      case _ =>
        warn(s"Unable to map Sierra location name to LocationType: $name")
        None
    }

  implicit class StringOps(s: String) {
    def hasSubstring(substrings: String*): Boolean =
      substrings.exists { s.contains }
  }
}
