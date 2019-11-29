package uk.ac.wellcome.models.work.internal

import enumeratum.{CirceEnum, Enum, EnumEntry}

sealed trait WorkType extends EnumEntry{
  val id: String
  val label: String
}

object WorkType extends Enum[WorkType] with CirceEnum[WorkType] {
  val values = findValues

  sealed trait UnlinkedWorkType extends WorkType
  sealed trait LinkedWorkType extends WorkType
  {
    val linksTo: UnlinkedWorkType
  }

  case object BooksWorkType extends UnlinkedWorkType {
    override val id: String = "a"
    override val label: String = "Books"
  }

  case object DigitalImagesWorkType extends UnlinkedWorkType {
    override val id: String = "q"
    override val label: String = "Digital Images"
  }

  case object EphemeraWorkType extends UnlinkedWorkType {
    override val id: String = "l"
    override val label: String = "Ephemera"
  }

  case object MapsWorkType extends UnlinkedWorkType {
    override val id: String = "e"
    override val label: String = "Maps"
  }

  case object PicturesWorkType extends UnlinkedWorkType {
    override val id: String = "k"
    override val label: String = "Pictures"
  }

  case object StudentDissertationsWorkType extends UnlinkedWorkType {
    override val id: String = "w"
    override val label: String = "Student dissertations"
  }

  case object `3DObjectsWorkType` extends UnlinkedWorkType {
    override val id: String = "r"
    override val label: String = "3-D Objects"
  }

  case object CDRomsWorkType extends UnlinkedWorkType {
    override val id: String = "m"
    override val label: String = "CD-Roms"
  }

  case object JournalsWorkType extends UnlinkedWorkType {
    override val id: String = "d"
    override val label: String = "Journals"
  }

  case object MixedMaterialsWorkType extends UnlinkedWorkType {
    override val id: String = "p"
    override val label: String = "Mixed materials"
  }

  case object AudioWorkType extends UnlinkedWorkType {
    override val id: String = "i"
    override val label: String = "Audio"
  }

  case object VideosWorkType extends UnlinkedWorkType {
    override val id: String = "g"
    override val label: String = "Videos"
  }

  case object ArchivesAndManuscriptsWorkType extends UnlinkedWorkType {
    override val id: String = "h"
    override val label: String = "Archives and manuscripts"
  }

  case object FilmWorkType extends UnlinkedWorkType {
    override val id: String = "n"
    override val label: String = "Film"
  }

  case object ManuscriptsAsianWorkType extends UnlinkedWorkType {
    override val id: String = "b"
    override val label: String = "Manuscripts, Asian"
  }

  case object MusicWorkType extends UnlinkedWorkType {
    override val id: String = "c"
    override val label: String = "Music"
  }

  case object StandingOrderWorkType extends UnlinkedWorkType {
    override val id: String = "u"
    override val label: String = "Standing order"
  }

  case object WebSitesOrderWorkType extends UnlinkedWorkType {
    override val id: String = "z"
    override val label: String = "Web sites"
  }

  case object EBooksWorkType extends LinkedWorkType {
    override val id: String = "v"
    override val label: String = "E-books"
    override val linksTo: UnlinkedWorkType = BooksWorkType
  }

  case object ESoundWorkType extends LinkedWorkType {
    override val id: String = "s"
    override val label: String = "E-sound"
    override val linksTo: UnlinkedWorkType = AudioWorkType
  }

  case object EJournalsWorkType extends LinkedWorkType {
    override val id: String = "j"
    override val label: String = "E-journals"
    override val linksTo: UnlinkedWorkType = JournalsWorkType
  }

  case object EVideosWorkType extends LinkedWorkType {
    override val id: String = "f"
    override val label: String = "E-videos"
    override val linksTo: UnlinkedWorkType = VideosWorkType
  }

  case object EManuscriptsAsianWorkType extends LinkedWorkType {
    override val id: String = "x"
    override val label: String = "E-manuscripts, Asian"
    override val linksTo: UnlinkedWorkType = ManuscriptsAsianWorkType
  }

}
