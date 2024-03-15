package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcElectronicResources
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.identifiers.TypedSierraRecordNumber
import weco.sierra.models.marc.VarField

// Create items with a DigitalLocation based on the contents of field 856.
//
// The 856 field is used to link to external resources, and it has a variety
// of uses at Wellcome.  Among other things, it links to websites, electronic
// journals, and links to canned searches in our catalogue.
//
// See RFC 035 Modelling MARC 856 "web linking entry"
// https://github.com/wellcomecollection/docs/tree/main/rfcs/035-marc-856
//
object SierraElectronicResources
    extends MarcElectronicResources
    with SierraMarcDataConversions {

  // We get the label by concatenating the contents of three subfields:
  //
  //  - ǂz (public note)
  //  - ǂy (link text)
  //  - ǂ3 (materials specified)
  //
  override def getLabel(field: MarcField): String =
    field.subfields
      .filter(sf => Seq("z", "y", "3").contains(sf.tag))
      .map { _.content.trim }
      .mkString(" ")

  override def getTitleAndLinkText(
    field: MarcField
  ): (Option[String], Option[String]) =
    getLabel(field) match {
      case "" => (None, None)
      case label =>
        if (
          label.split(" ").length <= 7 &&
          label.containsAnyOf("access", "view", "connect")
        )
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
                  "View resource [613.7 KB]"
                )
            )
          )
        else
          (Some(label), None)
    }

  def apply(
    id: TypedSierraRecordNumber,
    varFields: List[VarField]
  ): List[Item[IdState.Unminted]] = {
    implicit val ctx: LoggingContext = new LoggingContext(id.withCheckDigit)
    toItems(varFieldsAsMarcRecord(varFields)).toList
  }

  implicit class StringOps(s: String) {
    def containsAnyOf(substrings: String*): Boolean =
      substrings.exists { s.toLowerCase.contains(_) }
  }

}
