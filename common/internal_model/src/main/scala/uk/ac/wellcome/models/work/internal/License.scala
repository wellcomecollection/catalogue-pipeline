package uk.ac.wellcome.models.work.internal

import enumeratum.EnumEntry
import enumeratum.Enum
import io.circe.{Decoder, Encoder, Json}

sealed trait License extends EnumEntry {
  val id: String
  val label: String
  val url: Option[String]
  val ontologyType: String = "License"
}

object License extends Enum[License] {
  val values = findValues

  implicit val licenseEncoder: Encoder[License] = Encoder.instance[License] {
    license =>
      Json.obj(
        ("id", Json.fromString(license.id))
      )
  }

  implicit val licenseDecoder: Decoder[License] = Decoder.instance[License] {
    cursor =>
      for {
        id <- cursor.downField("id").as[String]
      } yield {
        createLicense(id)
      }
  }

  def createLicense(id: String): License =
    values.find(l => l.id == id) match {
      case Some(license) => license
      case _             => throw new Exception(s"$id is not a valid license id")
    }

  case object CCBY extends License {
    val id = "cc-by"
    val label = "Attribution 4.0 International (CC BY 4.0)"
    val url = Some("http://creativecommons.org/licenses/by/4.0/")
  }

  case object CCBYNC extends License {
    val id = "cc-by-nc"
    val label = "Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)"
    val url = Some("https://creativecommons.org/licenses/by-nc/4.0/")
  }

  case object CCBYNCND extends License {
    val id = "cc-by-nc-nd"
    val label =
      "Attribution-NonCommercial-NoDerivatives 4.0 International (CC BY-NC-ND 4.0)"
    val url = Some("https://creativecommons.org/licenses/by-nc-nd/4.0/")
  }

  case object CC0 extends License {
    val id = "cc-0"
    val label = "CC0 1.0 Universal"
    val url = Some(
      "https://creativecommons.org/publicdomain/zero/1.0/legalcode")
  }

  case object PDM extends License {
    val id = "pdm"
    val label = "Public Domain Mark"
    val url = Some(
      "https://creativecommons.org/share-your-work/public-domain/pdm/")
  }

  case object CopyrightNotCleared extends License {
    val id = "copyright-not-cleared"
    val label = "Copyright not cleared"
    val url: Option[String] = None
  }
}
