package uk.ac.wellcome.platform.transformer.sierra.generators

import uk.ac.wellcome.platform.transformer.sierra.source._
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.{
  SierraSourceLanguage,
  SierraSourceLocation
}
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.source_model.generators.SierraGenerators

trait SierraDataGenerators extends IdentifiersGenerators with SierraGenerators {
  def createSierraBibDataWith(
    lang: Option[SierraSourceLanguage] = None,
    materialType: Option[SierraMaterialType] = None,
    locations: Option[List[SierraSourceLocation]] = None,
    varFields: List[VarField] = List()
  ): SierraBibData =
    SierraBibData(
      lang = lang,
      materialType = materialType,
      locations = locations,
      varFields = varFields
    )

  def createSierraBibData: SierraBibData = createSierraBibDataWith()

  def createSierraItemDataWith(
    location: Option[SierraSourceLocation] = None,
    fixedFields: Map[String, FixedField] = Map(),
    varFields: List[VarField] = Nil
  ): SierraItemData =
    SierraItemData(
      location = location,
      fixedFields = fixedFields,
      varFields = varFields
    )

  def createSierraItemData: SierraItemData = createSierraItemDataWith()

  def createSierraOrderDataWith(
    fixedFields: Map[String, FixedField] = Map()
  ): SierraOrderData =
    SierraOrderData(
      fixedFields = fixedFields
    )

  def createSierraOrderData: SierraOrderData = createSierraOrderDataWith()
}
