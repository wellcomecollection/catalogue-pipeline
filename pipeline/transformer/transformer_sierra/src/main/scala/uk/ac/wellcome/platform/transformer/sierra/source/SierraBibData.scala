package uk.ac.wellcome.platform.transformer.sierra.source

import io.circe.{Decoder, DecodingFailure, HCursor}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.{
  SierraSourceCountry,
  SierraSourceLanguage,
  SierraSourceLocation
}

// https://techdocs.iii.com/sierraapi/Content/zReference/objects/bibObjectDescription.htm
// We only parse fields that we're going to use.
case class SierraBibData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  country: Option[SierraSourceCountry] = None,
  lang: Option[SierraSourceLanguage] = None,
  materialType: Option[SierraMaterialType] = None,
  locations: Option[List[SierraSourceLocation]] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
) {

  // The Sierra API returns varfields as a list.  When we look up values
  // in the Sierra transformer, we usually want to find all the varfields
  // with a particular MARC tag.
  //
  // It's more efficient to cache this lookup once, than repeatedly loop
  // through all the varFields to find it.  We record the original position
  // so we can recombine varFields with multiple tags in their original order.
  lazy val varFieldIndex: Map[String, List[(Int, VarField)]] =
    varFields
      .zipWithIndex
      .collect {
        case (vf @ VarField(_, Some(marcTag), _, _, _, _), position) =>
          (marcTag, position, vf)
      }
      .groupBy { case (marcTag, _, _) => marcTag }
      .map { case (marcTag, varFieldsWithPosition) =>
        marcTag ->
          varFieldsWithPosition
            .map { case (_, position, vf) => (position, vf) }
      }
}

object SierraBibData {

  // the "lang" field in sierra can be "lang": {"code": " "} for example for paintings that don't have a language.
  // We want those records to be decoded as `None` as this effectively means the record doesn't have a language,
  // so we use a custom decoder to achieve that
  implicit def d(implicit dec: Decoder[Option[SierraSourceLanguage]])
    : Decoder[Option[SierraSourceLanguage]] =
    dec.handleErrorWith { err: DecodingFailure =>
      Decoder.instance { hcursor: HCursor =>
        hcursor.downField("code").as[String].flatMap {
          case c if c.trim.isEmpty => Right(None)
          case _                   => Left(err)
        }
      }
    }
}
