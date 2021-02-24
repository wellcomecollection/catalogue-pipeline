package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.LocationType.OnlineResource
import uk.ac.wellcome.models.work.internal.{DigitalLocation, IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, SierraQueryOps, VarField}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

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
  def apply(bibId: SierraBibNumber, varFields: List[VarField]): List[Item[IdState.Unminted]] =
    varFields
      .filter { _.marcTag.contains("856") }
      .flatMap { vf => createItem(bibId, vf) }

  private def createItem(bibId: SierraBibNumber, vf: VarField): Option[Item[IdState.Unminted]] = {
    assert(vf.marcTag.contains("856"))

    getUrl(bibId, vf).map { url =>

      // We don't want the link text to be too long (at most seven words), so
      // we apply the following heuristic to the label:
      //
      // If the concatenated string is seven words or less, and contains "access",
      // "view" or "connect", we put it in the location "linkText" field.
      // Otherwise, we put it in the item's "title" field.
      val label = getLabel(vf)

      Item(
        title = label,
        locations = List(
          DigitalLocation(url = url, locationType = OnlineResource)
        )
      )
    }
  }

  // We take the URL from subfield ǂu.  If subfield ǂu is missing, repeated,
  // or contains something other than a URL, we discard it.
  private def getUrl(bibId: SierraBibNumber, vf: VarField): Option[String] =
    vf.subfieldsWithTag("u") match {
      case Seq(MarcSubfield(_, content)) if isUrl(content) => Some(content)

      case Seq(MarcSubfield(_, content)) =>
        warn(s"Bib $bibId has a value in 856 ǂu which isn't a URL: $content")
        None

      case Nil =>
        warn(s"Bib $bibId has a field 856 without any URLs")
        None

      case other =>
        warn(s"Bib $bibId has a field 856 with repeated subfield ǂu")
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
        .map { _.content }
        .mkString(" ")

    if (labelCandidate.isEmpty) {
      None
    } else {
      Some(labelCandidate)
    }
  }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess
}
