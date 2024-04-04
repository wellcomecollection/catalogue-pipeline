package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcElectronicResources
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.identifiers.TypedSierraRecordNumber
import weco.sierra.models.marc.VarField
import scala.util.{Failure, Success, Try}

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

  // The labels derived from the subfields of 856 in Sierra are not consistent
  // in their intent.  Some represent a name for the resource, others are a
  // call to action, e.g. inviting the user to access some remote service.
  // Some are terse, others exceptionally verbose.
  //
  // In order to be consistent, the link text is expected to contain a call to
  // action, whereas the title contains a name for the resource.
  //
  // By maintaining this distinction, we can allow clients to consistently
  // present Call-To-Action links without worrying that the title is also
  // imperative.
  //
  // We don't want the link text to be too long (at most seven words), so
  // we apply the following heuristic to the label:
  //
  // If the concatenated string is seven words or less, and contains "access",
  // "view" or "connect", we put it in the location "linkText" field.
  // Otherwise, we put it in the item's "title" field.
<<<<<<< HEAD
  override def getTitleOrLinkText(
    field: MarcField
  ): Try[Either[String, String]] =
    getLabel(field) match {
      case "" =>
        Failure(
          new Exception(s"could not construct a label from 856 field $field")
=======
  override protected def getTitleOrLinkText(
    field: MarcField
  )(implicit ctx: LoggingContext): Try[Either[String, String]] =
    getLabel(field) match {
      case "" =>
        Failure(
          new Exception(
            ctx(s"could not construct a label from 856 field $field")
          )
>>>>>>> main
        )
      case label =>
        Success(
          if (
            label.split(" ").length <= 7 &&
            label.containsAnyOf("access", "view", "connect")
          )
            Right(
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
          else
            Left(label)
        )
    }

  def apply(
    id: TypedSierraRecordNumber,
    varFields: List[VarField]
  ): List[Item[IdState.Unminted]] = {
    implicit val ctx: LoggingContext = LoggingContext(id.withCheckDigit)
    toItems(varFieldsAsMarcRecord(varFields)).toList
  }

  implicit class StringOps(s: String) {
    def containsAnyOf(substrings: String*): Boolean =
      substrings.exists { s.toLowerCase.contains(_) }
  }

}
