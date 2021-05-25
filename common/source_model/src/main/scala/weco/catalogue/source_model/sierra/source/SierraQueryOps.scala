package weco.catalogue.source_model.sierra.source

import grizzled.slf4j.Logging
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}
import weco.catalogue.source_model.sierra.{SierraBibData, SierraItemData}

trait SierraQueryOps extends Logging {

  implicit class BibDataOps(bibData: SierraBibData) {

    // Return a list of all the VarFields in the bib data that match
    // any of these MARC tags.
    //
    // VarFields are returned in the same order as in the original bib.
    def varfieldsWithTags(tags: String*): List[VarField] =
      tags
        .flatMap { t =>
          bibData.varFieldIndex.get(t)
        }
        .flatten
        .sortBy { case (position, _) => position }
        .collect { case (_, vf) => vf }
        .toList

    // Return all the VarFields in the bib data with this MARC tag.
    //
    // VarFields are returned in the same order as in the original bib.
    def varfieldsWithTag(tag: String): List[VarField] =
      varfieldsWithTags(tag)

    def nonrepeatableVarfieldWithTag(tag: String): Option[VarField] =
      varfieldsWithTag(tag) match {
        case Seq(vf) => Some(vf)
        case Nil     => None
        case multiple =>
          warn(
            s"Multiple instances of non-repeatable varfield with tag $tag: $multiple"
          )
          Some(multiple.head)
      }

    def subfieldsWithTags(tags: (String, String)*): List[MarcSubfield] =
      tags.toList.flatMap {
        case (tag, subfieldTag) =>
          varfieldsWithTag(tag).subfieldsWithTag(subfieldTag)
      }

    def subfieldsWithTag(tag: (String, String)): List[MarcSubfield] =
      subfieldsWithTags(tag)
  }

  implicit class VarFieldsOps(varfields: List[VarField]) {
    def withFieldTags(tags: String*): List[VarField] =
      varfields
        .filter { _.fieldTag.exists(tag => tags.contains(tag)) }
        .sortBy { varfield =>
          tags.indexOf(varfield.fieldTag.get)
        }

    def withFieldTag(tag: String): List[VarField] = withFieldTags(tag)

    def withIndicator1(ind: String): List[VarField] =
      varfields.filter(_.indicator1.contains(ind))

    def withIndicator2(ind: String): List[VarField] =
      varfields.filter(_.indicator2.contains(ind))

    def subfields: List[MarcSubfield] = varfields.flatMap(_.subfields)

    def subfieldsWithTags(tags: String*): List[MarcSubfield] =
      varfields.subfields.withTags(tags: _*)

    def subfieldsWithoutTags(tags: String*): List[MarcSubfield] =
      varfields.subfields.withoutTags(tags: _*)

    def subfieldsWithTag(tag: String): List[MarcSubfield] =
      subfieldsWithTags(tag)

    def nonrepeatableSubfieldWithTag(tag: String): Option[MarcSubfield] =
      subfieldsWithTag(tag) match {
        case Seq(sf) => Some(sf)
        case Nil     => None
        case multiple =>
          warn(
            s"Multiple instances of non-repeatable subfield with tag ǂ$tag: $multiple"
          )
          Some(
            MarcSubfield(
              tag = tag,
              content = multiple.map { _.content }.mkString(" "))
          )
      }

    def contents: List[String] = varfields.flatMap(_.content)

    def subfieldContents: List[String] = varfields.subfields.contents
  }

  // See the Sierra documentation "Fixed fields on items"
  // https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
  implicit class ItemDataOps(itemData: SierraItemData) {
    def imessage: Option[String] =
      itemData.fixedFields.get("97").map { _.value.trim }

    def status: Option[String] =
      itemData.fixedFields.get("88").map { _.value.trim }

    def loanRule: Option[String] =
      itemData.fixedFields.get("87").map { _.value.trim }

    def locationCode: Option[String] =
      itemData.fixedFields.get("79").map { _.value.trim }

    def itemType: Option[String] =
      itemData.fixedFields.get("61").map { _.value.trim }

    def opacmsg: Option[String] =
      itemData.fixedFields.get("108").map { _.value.trim }
  }

  implicit class SubfieldsOps(subfields: List[MarcSubfield]) {

    def withTags(tags: String*): List[MarcSubfield] =
      subfields
        .filter { subfield =>
          tags.contains(subfield.tag)
        }

    def withTag(tag: String): List[MarcSubfield] = withTags(tag)

    def withoutTags(tags: String*): List[MarcSubfield] =
      subfields
        .filterNot { subfield =>
          tags.contains(subfield.tag)
        }

    def contents: List[String] = subfields.map(_.content)

    def firstContent: Option[String] = subfields.contents.headOption

    def contentString(sep: String): Option[String] =
      contents match {
        case Nil     => None
        case strings => Some(strings.mkString(sep))
      }
  }

  import scala.language.implicitConversions

  implicit def varfieldOps(varfield: VarField): VarFieldsOps =
    new VarFieldsOps(List(varfield))
}
