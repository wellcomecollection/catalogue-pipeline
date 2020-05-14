package uk.ac.wellcome.platform.transformer.sierra.source

import io.circe.{Decoder, DecodingFailure, HCursor}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.{SierraSourceCountry, SierraSourceLanguage, SierraSourceLocation}

// https://techdocs.iii.com/sierraapi/Content/zReference/objects/bibObjectDescription.htm
case class SierraBibData(
  title: Option[String] = None,
  deleted: Boolean = false,
  suppressed: Boolean = false,
  country: Option[SierraSourceCountry] = None,
  lang: Option[SierraSourceLanguage] = None,
  materialType: Option[SierraMaterialType] = None,
  locations: Option[List[SierraSourceLocation]] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)

object SierraBibData {

  // the "lang" field in sierra can be "lang": {"code": " "} for example for paintings that don't have a language.
  // We want those records to be decoded as `None` as this effectively means the record doesn't have a language,
  // so we use a custom decoder to achieve that
  implicit def d(implicit dec: Decoder[Option[SierraSourceLanguage]]): Decoder[Option[SierraSourceLanguage]] =
    dec.handleErrorWith{err: DecodingFailure =>
      Decoder.instance{ hcursor: HCursor =>
      hcursor.downField("code").as[String].flatMap {
        case c if c.trim.isEmpty => Right(None)
        case _ => Left(err)
      }
    }}
}
