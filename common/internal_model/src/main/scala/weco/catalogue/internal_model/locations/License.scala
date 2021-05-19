package weco.catalogue.internal_model.locations

import enumeratum.EnumEntry
import enumeratum.Enum
import io.circe.{Decoder, Encoder}
import org.scanamo.DynamoFormat

sealed trait License extends EnumEntry {
  val id: String
  val label: String
  val url: String

  override lazy val entryName: String = id
}

object License extends Enum[License] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for License are not unique!"
  )

  implicit val licenseEncoder: Encoder[License] =
    Encoder.forProduct1("id")(_.id)

  implicit val licenseDecoder: Decoder[License] =
    Decoder.forProduct1("id")(License.withName)

  implicit val format: DynamoFormat[License] =
    DynamoFormat.coercedXmap[License, String, NoSuchElementException](
      License.withName,
      _.id
    )

  case object CCBY extends License {
    val id = "cc-by"
    val label = "Attribution 4.0 International (CC BY 4.0)"
    val url = "http://creativecommons.org/licenses/by/4.0/"
  }

  case object CCBYNC extends License {
    val id = "cc-by-nc"
    val label = "Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)"
    val url = "https://creativecommons.org/licenses/by-nc/4.0/"
  }

  case object CCBYNCND extends License {
    val id = "cc-by-nc-nd"
    val label =
      "Attribution-NonCommercial-NoDerivatives 4.0 International (CC BY-NC-ND 4.0)"
    val url = "https://creativecommons.org/licenses/by-nc-nd/4.0/"
  }

  case object CC0 extends License {
    val id = "cc-0"
    val label = "CC0 1.0 Universal"
    val url = "https://creativecommons.org/publicdomain/zero/1.0/legalcode"
  }

  case object PDM extends License {
    val id = "pdm"
    val label = "Public Domain Mark"
    val url = "https://creativecommons.org/share-your-work/public-domain/pdm/"
  }

  case object CCBYND extends License {
    val id = "cc-by-nd"
    val label =
      "Attribution-NoDerivatives 4.0 International (CC BY-ND 4.0)"
    val url = "https://creativecommons.org/licenses/by-nd/4.0/"
  }

  case object CCBYSA extends License {
    val id = "cc-by-sa"
    val label =
      "Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)"
    val url = "https://creativecommons.org/licenses/by-sa/4.0/"
  }

  case object CCBYNCSA extends License {
    val id = "cc-by-nc-sa"
    val label =
      "Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)"
    val url = "https://creativecommons.org/licenses/by-nc-sa/4.0/"
  }

  case object OGL extends License {
    val id = "ogl"
    val label = "Open Government Licence"
    val url =
      "http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/"
  }

  case object OPL extends License {
    val id = "opl"
    val label = "Open Parliament Licence"
    val url =
      "https://www.parliament.uk/site-information/copyright-parliament/open-parliament-licence/"
  }

  case object InCopyright extends License {
    val id: String = "inc"
    val label: String = "In copyright"
    val url = "http://rightsstatements.org/vocab/InC/1.0/"
  }
}
