package uk.ac.wellcome.platform.transformer.sierra.source

trait SierraQueryOps {

  implicit class BibDataOps(bibData: SierraBibData) {

    val varfields: List[VarField] = bibData.varFields

    def varfieldsWithTags(tags: String*): List[VarField] =
      bibData.varFields.withMarcTags(tags: _*)

    def varfieldsWithTag(tag: String): List[VarField] =
      varfieldsWithTags(tag)

    def subfieldsWithTags(tags: (String, String)*): List[MarcSubfield] =
      tags.toList.flatMap {
        case (tag, subfieldTag) =>
          varfieldsWithTag(tag).subfieldsWithTag(subfieldTag)
      }

    def subfieldsWithTag(tag: (String, String)): List[MarcSubfield] =
      subfieldsWithTags(tag)
  }

  implicit class VarFieldsOps(varfields: List[VarField]) {

    def withMarcTags(tags: String*): List[VarField] =
      varfields
        .filter { _.marcTag.exists(tag => tags.contains(tag)) }
        .sortBy { varfield =>
          tags.indexOf(varfield.marcTag.get)
        }

    def withFieldTags(tags: String*): List[VarField] =
      varfields
        .filter { _.fieldTag.exists(tag => tags.contains(tag)) }
        .sortBy { varfield =>
          tags.indexOf(varfield.fieldTag.get)
        }

    def withFieldTag(tag: String): List[VarField] = withFieldTags(tag)
    def withMarcTag(tag: String): List[VarField] = withMarcTags(tag)

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

    def contents: List[String] = varfields.flatMap(_.content)

    def subfieldContents: List[String] = varfields.subfields.contents

    def firstContent: Option[String] = varfields.contents.headOption

    def contentString(sep: String): Option[String] =
      contents.mkStringOrNone(sep)

    def contentString: Option[String] = contents.mkStringOrNone
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

    def contentString: Option[String] = contents.mkStringOrNone
  }

  implicit class StringSeqOps(strings: Seq[String]) {

    def mkStringOrNone(sep: String): Option[String] =
      strings match {
        case Nil     => None
        case strings => Some(strings.mkString(sep))
      }

    def mkStringOrNone: Option[String] =
      strings match {
        case Nil     => None
        case strings => Some(strings.mkString)
      }
  }

  import scala.language.implicitConversions

  implicit def varfieldOps(varfield: VarField): VarFieldsOps =
    new VarFieldsOps(List(varfield))
}
