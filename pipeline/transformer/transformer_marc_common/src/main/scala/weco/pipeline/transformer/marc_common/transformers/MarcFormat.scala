package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.work.Format
import weco.pipeline.transformer.marc_common.models.MarcRecord

/** https://www.loc.gov/marc/bibliographic/bd008s.html "23 - Form of item
  * (006/06)" The spec for field 008 contains a list of material specific
  * details including the form of item, the code for which matches the value
  * found in the 006 field at position 6.
  */
sealed trait FormOfItem
object Marc008Field {
  object FormOfItem {
    case object Online extends FormOfItem
  }
}

// Used to transform data from a MARC field where the data is positional
// e.g. the leader and some control fields
sealed trait MarcPositionalElement extends MarcDataTransformer {
  val position: Int
  val length: Int = 1
}

/** https://www.loc.gov/marc/bibliographic/bd006.html "Additional Material
  * Characteristics" Marc 006 field is a control field that contains additional
  * material characteristics, based on the form of material.
  *
  * For example, for continuing resources, the 006 field contains the form of
  * item at position 6, but for visual materials it's at position 12.
  *
  * We infer the form of material based on values in the leader field.
  *
  * At present we only care about the form of item for books and continuing
  * resources, in order to identify e-books and e-journals (o in position 6).
  * This could be extended to other forms of material in the future.
  */
object Marc006Field {
  trait FormOfItemField extends MarcPositionalElement {
    type Output = Option[FormOfItem]

    val position = 6

    def apply(record: MarcRecord): Option[FormOfItem] = {
      record
        .controlField("006")
        .map(_.content)
        .map(_.slice(position, position + length))
        .flatMap {
          case "o" => Some(Marc008Field.FormOfItem.Online)
          case _   => None
        }
    }
  }

  case object Books {
    case object FormOfItemField extends FormOfItemField
  }

  case object ContinuingResource {
    case object FormOfItemField extends FormOfItemField
  }
}

/** https://www.loc.gov/marc/bibliographic/bdleader.html "Leader" The leader
  * field contains information about the record as a whole. We use it to
  * determine the type of record and the bibliographic level, which is used to
  * determine the format of the material.
  *
  * For example the type of record for a continuing resource is 'a' (language
  * material) in position 6 (type of record), and 's' (serial) in position 7
  * (bibliographic level).
  *
  * At present we only care about the type of record and bibliographic level for
  * books and continuing resources, in order to indentify e-books and
  * e-journals. This could be extended to other types of material in the future.
  */
object MarcLeader {
  // https://www.loc.gov/marc/bibliographic/bdleader.html "06 - Type of record"
  case object TypeOfRecord extends MarcPositionalElement {
    sealed trait MarcLeaderTypeOfRecord

    case object LanguageMaterial extends MarcLeaderTypeOfRecord

    type Output = Option[MarcLeaderTypeOfRecord]

    val position = 6

    def apply(record: MarcRecord): Option[MarcLeaderTypeOfRecord] = {
      val leader = record.leader
      val typeOfRecord = leader.slice(position, position + length)

      typeOfRecord match {
        case "a" => Some(LanguageMaterial)
        case _   => None
      }
    }
  }

  // https://www.loc.gov/marc/bibliographic/bdleader.html "07 - Bibliographic level"
  case object BibliographicLevel extends MarcPositionalElement {
    sealed trait MarcLeaderBibliographicLevel

    case object Monograph extends MarcLeaderBibliographicLevel
    case object Serial extends MarcLeaderBibliographicLevel

    type Output = Option[MarcLeaderBibliographicLevel]

    val position = 7

    def apply(record: MarcRecord): Option[MarcLeaderBibliographicLevel] = {
      val leader = record.leader
      val bibliographicLevel = leader.slice(position, position + length)

      bibliographicLevel match {
        case "m" => Some(Monograph)
        case "s" => Some(Serial)
        case _   => None
      }
    }
  }
}

object MarcFormat extends MarcDataTransformer {
  type Output = Option[Format]

  /** This function maps a MARC record to a Format, if possible.
    *
    * We do this by inspecting the leader field, then the 006 field, and
    * referring 008 material specific details (though we do not read the 008
    * field directly).
    */
  def apply(record: MarcRecord): Option[Format] = {
    val (material, leader, form) = (
      MarcLeader.TypeOfRecord(record),
      MarcLeader.BibliographicLevel(record)
    ) match {
      case (
            material @ Some(MarcLeader.TypeOfRecord.LanguageMaterial),
            level @ Some(MarcLeader.BibliographicLevel.Serial)
          ) =>
        (
          material,
          level,
          Marc006Field.ContinuingResource.FormOfItemField(record)
        )

      case (
            material @ Some(MarcLeader.TypeOfRecord.LanguageMaterial),
            level @ Some(MarcLeader.BibliographicLevel.Monograph)
          ) =>
        (material, level, Marc006Field.Books.FormOfItemField(record))

      case _ => (None, None, None)
    }

    (material, leader, form) match {
      case (
            Some(MarcLeader.TypeOfRecord.LanguageMaterial),
            Some(MarcLeader.BibliographicLevel.Serial),
            Some(Marc008Field.FormOfItem.Online)
          ) =>
        Some(Format.EJournals)
      case (
            Some(MarcLeader.TypeOfRecord.LanguageMaterial),
            Some(MarcLeader.BibliographicLevel.Monograph),
            Some(Marc008Field.FormOfItem.Online)
          ) =>
        Some(Format.EBooks)
      case _ => None
    }
  }
}
