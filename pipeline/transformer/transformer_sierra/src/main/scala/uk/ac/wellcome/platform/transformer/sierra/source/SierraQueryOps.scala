package uk.ac.wellcome.platform.transformer.sierra.source

import uk.ac.wellcome.platform.transformer.sierra.exceptions.ShouldNotTransformException

trait SierraQueryOps {

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
        case fields =>
          throw new ShouldNotTransformException(
            s"Multiple instances of non-repeatable varfield with tag $tag: $fields"
          )
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
        case fields =>
          throw new ShouldNotTransformException(
            s"Multiple instances of non-repeatable subfield with tag ǂ$tag: $fields"
          )
      }

    def contents: List[String] = varfields.flatMap(_.content)

    def subfieldContents: List[String] = varfields.subfields.contents

    def firstContent: Option[String] = varfields.contents.headOption

    def contentString(sep: String): Option[String] =
      contents.mkStringOrNone(sep)

    def contentString: Option[String] =
      contentString(sep = "")
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
      contents.mkStringOrNone(sep)

    def contentString: Option[String] =
      contentString(sep = "")
  }

  implicit class StringSeqOps(maybeStrings: Seq[String]) {
    def mkStringOrNone(sep: String): Option[String] =
      maybeStrings match {
        case Nil     => None
        case strings => Some(strings.mkString(sep))
      }
  }

  import scala.language.implicitConversions

  implicit def varfieldOps(varfield: VarField): VarFieldsOps =
    new VarFieldsOps(List(varfield))
}
