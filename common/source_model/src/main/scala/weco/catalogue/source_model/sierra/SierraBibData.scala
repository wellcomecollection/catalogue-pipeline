package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.source.{
  SierraMaterialType,
  SierraSourceCountry,
  SierraSourceLanguage,
  SierraSourceLocation
}
import weco.sierra.models.marc.{FixedField, VarField}

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
    varFields.zipWithIndex
      .collect {
        case (vf @ VarField(_, Some(marcTag), _, _, _, _), position) =>
          (marcTag, position, vf)
      }
      .groupBy { case (marcTag, _, _) => marcTag }
      .map {
        case (marcTag, varFieldsWithPosition) =>
          marcTag ->
            varFieldsWithPosition
              .map { case (_, position, vf) => (position, vf) }
      }
}
