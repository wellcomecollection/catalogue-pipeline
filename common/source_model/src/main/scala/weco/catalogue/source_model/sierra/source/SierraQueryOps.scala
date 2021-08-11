package weco.catalogue.source_model.sierra.source

import grizzled.slf4j.Logging
import weco.catalogue.source_model.sierra.{SierraBibData, SierraItemData}
import weco.sierra.models.marc.{Subfield, VarField}

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

    def subfieldsWithTags(tags: (String, String)*): List[Subfield] =
      tags.toList.flatMap {
        case (tag, subfieldTag) =>
          varfieldsWithTag(tag).subfieldsWithTag(subfieldTag)
      }

    def subfieldsWithTag(tag: (String, String)): List[Subfield] =
      subfieldsWithTags(tag)
  }

  implicit class ItemDataOps(itemData: SierraItemData) {
    def displayNote: Option[String] = {
      val varfields = itemData.varFields
        .filter { _.fieldTag.contains("n") }
        .collect {
          case VarField(Some(content), _, _, _, _, Nil) =>
            content

          case VarField(None, _, _, _, _, subfields) =>
            subfields.map { _.content }.mkString(" ")
        }
        .filterNot(_.isEmpty)
        .distinct

      if (varfields.isEmpty) {
        None
      } else {
        Some(varfields.mkString("\n\n").trim.replace("<p>", ""))
      }
    }
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

    def subfields: List[Subfield] = varfields.flatMap(_.subfields)

    def subfieldsWithTags(tags: String*): List[Subfield] =
      varfields.subfields.withTags(tags: _*)

    def subfieldsWithoutTags(tags: String*): List[Subfield] =
      varfields.subfields.withoutTags(tags: _*)

    def subfieldsWithTag(tag: String): List[Subfield] =
      subfieldsWithTags(tag)

    def nonrepeatableSubfieldWithTag(tag: String): Option[Subfield] =
      subfieldsWithTag(tag) match {
        case Seq(sf) => Some(sf)
        case Nil     => None
        case multiple =>
          warn(
            s"Multiple instances of non-repeatable subfield with tag ǂ$tag: $multiple"
          )
          Some(
            Subfield(
              tag = tag,
              content = multiple.map { _.content }.mkString(" ")
            )
          )
      }

    def contents: List[String] = varfields.flatMap(_.content)

    def subfieldContents: List[String] = varfields.subfields.contents
  }

  implicit class SubfieldsOps(subfields: List[Subfield]) {

    def withTags(tags: String*): List[Subfield] =
      subfields
        .filter { subfield =>
          tags.contains(subfield.tag)
        }

    def withTag(tag: String): List[Subfield] = withTags(tag)

    def withoutTags(tags: String*): List[Subfield] =
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
