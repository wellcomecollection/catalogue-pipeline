package uk.ac.wellcome.platform.transformer.sierra.generators

import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.transformer.sierra.source._
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.{
  SierraSourceLanguage,
  SierraSourceLocation
}
import weco.catalogue.sierra_adapter.generators.SierraGenerators

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
    deleted: Boolean = false,
    location: Option[SierraSourceLocation] = None,
    callNumber: Option[String] = None,
    varFields: List[VarField] = Nil
  ): SierraItemData =
    SierraItemData(
      deleted = deleted,
      location = location,
      callNumber = callNumber,
      varFields = varFields
    )

  def createSierraItemData: SierraItemData = createSierraItemDataWith()

  def createSierraMaterialTypeWith(code: String): SierraMaterialType =
    SierraMaterialType(code = code)
}
