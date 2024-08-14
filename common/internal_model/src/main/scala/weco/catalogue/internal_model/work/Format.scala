package weco.catalogue.internal_model.work

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}

sealed trait Format extends EnumEntry {
  val id: String
  val label: String

  override lazy val entryName: String = id
}

object Format extends Enum[Format] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for Format are not unique!"
  )

  implicit val formatEncoder: Encoder[Format] =
    Encoder.forProduct2("id", "label")(format => (format.id, format.label))

  implicit val formatDecoder: Decoder[Format] =
    Decoder.forProduct2("id", "label")(
      (id: String, _: String) => Format.withName(id)
    )

  def fromCode(id: String): Option[Format] =
    Format.withNameOption(id)

  sealed trait Unlinked extends Format
  sealed trait Linked extends Format {
    val linksTo: Unlinked
  }

  sealed trait Audiovisual extends Format

  case object Books extends Unlinked {
    override val id: String = "a"
    override val label: String = "Books"
  }

  case object DigitalImages extends Unlinked {
    override val id: String = "q"
    override val label: String = "Digital Images"
  }

  case object Ephemera extends Unlinked {
    override val id: String = "l"
    override val label: String = "Ephemera"
  }

  case object Maps extends Unlinked {
    override val id: String = "e"
    override val label: String = "Maps"
  }

  case object Pictures extends Unlinked {
    override val id: String = "k"
    override val label: String = "Pictures"
  }

  case object StudentDissertations extends Unlinked {
    override val id: String = "w"
    override val label: String = "Student dissertations"
  }

  case object `3DObjects` extends Unlinked {
    override val id: String = "r"
    override val label: String = "3-D Objects"
  }

  case object CDRoms extends Unlinked {
    override val id: String = "m"
    override val label: String = "CD-Roms"
  }

  case object Journals extends Unlinked {
    override val id: String = "d"
    override val label: String = "Journals"
  }

  case object MixedMaterials extends Unlinked {
    override val id: String = "p"
    override val label: String = "Mixed materials"
  }

  case object Audio extends Unlinked with Audiovisual {
    override val id: String = "i"
    override val label: String = "Audio"
  }

  case object Videos extends Unlinked with Audiovisual {
    override val id: String = "g"
    override val label: String = "Videos"
  }

  case object ArchivesAndManuscripts extends Unlinked {
    override val id: String = "h"
    override val label: String = "Archives and manuscripts"
  }

  case object ArchivesDigital extends Unlinked {
    // This format is found in CALM material type, not Sierra.
    // We have been using ArchivesAndManuscripts as the format
    // for all CALM sourced works, hence the id here is prefixed
    // with a "h" to namespace it within "ArchivesAndManuscripts".
    override val id: String = "hdig"
    override val label: String = "Archives (Digital)"
  }

  case object Film extends Unlinked with Audiovisual {
    override val id: String = "n"
    override val label: String = "Film"
  }

  case object ManuscriptsAsian extends Unlinked {
    override val id: String = "b"
    override val label: String = "Manuscripts"
  }

  case object Music extends Unlinked with Audiovisual {
    override val id: String = "c"
    override val label: String = "Music"
  }

  case object StandingOrder extends Unlinked {
    override val id: String = "u"
    override val label: String = "Standing order"
  }

  case object WebSites extends Unlinked {
    override val id: String = "z"
    override val label: String = "Web sites"
  }

  case object EBooks extends Linked {
    override val id: String = "v"
    override val label: String = "E-books"
    override val linksTo: Unlinked = Books
  }

  case object ESound extends Linked with Audiovisual {
    override val id: String = "s"
    override val label: String = "E-sound"
    override val linksTo: Unlinked = Audio
  }

  case object EJournals extends Linked {
    override val id: String = "j"
    override val label: String = "E-journals"
    override val linksTo: Unlinked = Journals
  }

  case object EVideos extends Linked with Audiovisual {
    override val id: String = "f"
    override val label: String = "E-videos"
    override val linksTo: Unlinked = Videos
  }

  case object EManuscriptsAsian extends Linked {
    override val id: String = "x"
    override val label: String = "Manuscripts"
    override val linksTo: Unlinked = ManuscriptsAsian
  }

}
