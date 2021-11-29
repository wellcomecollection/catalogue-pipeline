package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.LocationType.OnlineResource
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation
}
import weco.catalogue.internal_model.work.Item
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.identifiers.TypedSierraRecordNumber
import weco.sierra.models.marc.{Subfield, VarField}

import java.net.URL
import scala.util.Try

// Create items with a DigitalLocation based on the contents of field 856.
//
// The 856 field is used to link to external resources, and it has a variety
// of uses at Wellcome.  Among other things, it links to websites, electronic
// journals, and links to canned searches in our catalogue.
//
// See RFC 035 Modelling MARC 856 "web linking entry"
// https://github.com/wellcomecollection/docs/pull/48
//
// TODO: Update this link to the published version of the RFC
//
object SierraElectronicResources extends SierraQueryOps with Logging {
  def apply(id: TypedSierraRecordNumber,
            varFields: List[VarField]): List[Item[IdState.Unminted]] =
    varFields
      .filter { _.marcTag.contains("856") }
      .flatMap { vf =>
        createItem(id, vf)
      }

  private def createItem(id: TypedSierraRecordNumber,
                         vf: VarField): Option[Item[IdState.Unminted]] = {
    assert(vf.marcTag.contains("856"))

    // 856 indicator 2 takes the following values:
    //
    //      Relationship
    //      # - No information provided
    //      0 - Resource
    //      1 - Version of resource
    //      2 - Related resource
    //      8 - No display constant generated
    //
    // This allows us to include URLs that are related to the work, but not the
    // work itself (e.g. a description on a publisher website).
    val status = vf.indicator2 match {
      case Some("2") =>
        AccessStatus.LicensedResources(
          relationship = LicensedResources.RelatedResource)
      case _ =>
        AccessStatus.LicensedResources(
          relationship = LicensedResources.Resource)
    }

    getUrl(id, vf).map { url =>
      // We don't want the link text to be too long (at most seven words), so
      // we apply the following heuristic to the label:
      //
      // If the concatenated string is seven words or less, and contains "access",
      // "view" or "connect", we put it in the location "linkText" field.
      // Otherwise, we put it in the item's "title" field.
      val (title, linkText) = getLabel(vf) match {
        case Some(label) =>
          if (label.split(" ").length <= 7 &&
              label.containsAnyOf("access", "view", "connect"))
            (
              None,
              Some(
                label
                // e.g. "View resource." ~> "View resource"
                  .stripSuffix(".")
                  .stripSuffix(":")
                  // e.g. "view resource" ~> "View resource"
                  .replaceFirst("^view ", "View ")
                  // These are hard-coded fixes for a couple of known weird records.
                  // We could also fix these in the catalogue, but fixing them here
                  // is cheap and easy.
                  .replace("VIEW FULL TEXT", "View full text")
                  .replace("via  MyiLibrary", "via MyiLibrary")
                  .replace("youtube", "YouTube")
                  .replace("View resource {PDF", "View resource [PDF")
                  .replace(
                    "View resource 613.7 KB]",
                    "View resource [613.7 KB]"))
            )
          else
            (Some(label), None)

        case None => (None, None)
      }

      Item(
        title = title,
        locations = List(
          DigitalLocation(
            url = url,
            linkText = linkText,
            locationType = OnlineResource,
            // We want these works to show up in a filter for "available online",
            // so we need to add an access status.
            //
            // Neither "Open" nor "Open with advisory" are appropriate.
            //
            // See https://github.com/wellcomecollection/platform/issues/5062 for
            // more discussion and conversations about this.
            accessConditions = List(
              AccessCondition(method = AccessMethod.ViewOnline, status = status)
            )
          )
        )
      )
    }
  }

  // We take the URL from subfield ǂu.  If subfield ǂu is missing, repeated,
  // or contains something other than a URL, we discard it.
  private def getUrl(id: TypedSierraRecordNumber,
                     vf: VarField): Option[String] =
    vf.subfieldsWithTag("u") match {
      case Seq(Subfield(_, content)) if isUrl(content) => Some(content)

      case Seq(Subfield(_, content)) =>
        warn(
          s"${id.withCheckDigit} has a value in 856 ǂu which isn't a URL: $content")
        None

      case Nil =>
        warn(s"${id.withCheckDigit} has a field 856 without any URLs")
        None

      case other =>
        warn(s"${id.withCheckDigit} has a field 856 with repeated subfield ǂu")
        None
    }

  // We get the label by concatenating the contents of three subfields:
  //
  //  - ǂz (public note)
  //  - ǂy (link text)
  //  - ǂ3 (materials specified)
  //
  private def getLabel(vf: VarField): Option[String] = {
    val labelCandidate =
      vf.subfieldsWithTags("z", "y", "3")
        .map { _.content.trim }
        .mkString(" ")

    if (labelCandidate.isEmpty) {
      None
    } else {
      Some(labelCandidate)
    }
  }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess

  implicit class StringOps(s: String) {
    def containsAnyOf(substrings: String*): Boolean =
      substrings.exists { s.toLowerCase.contains(_) }
  }
}
