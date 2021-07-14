package weco.catalogue.source_model.generators

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.source_model
import weco.catalogue.source_model.sierra.identifiers.SierraItemNumber
import weco.catalogue.source_model.sierra.marc.{FixedField, VarField}
import weco.catalogue.source_model.sierra.source.{SierraMaterialType, SierraSourceLanguage, SierraSourceLocation}
import weco.catalogue.source_model.sierra.{SierraBibData, SierraItemData, SierraOrderData}

trait SierraDataGenerators extends IdentifiersGenerators with SierraGenerators {
  def createSierraBibDataWith(
    lang: Option[SierraSourceLanguage] = None,
    materialType: Option[SierraMaterialType] = None,
    locations: Option[List[SierraSourceLocation]] = None,
    varFields: List[VarField] = List()
  ): SierraBibData =
    source_model.sierra.SierraBibData(
      lang = lang,
      materialType = materialType,
      locations = locations,
      varFields = varFields
    )

  def createSierraBibData: SierraBibData = createSierraBibDataWith()

  def createSierraItemDataWith(
                                id: SierraItemNumber = createSierraItemNumber,
                                location: Option[SierraSourceLocation] = None,
                                copyNo: Option[Int] = None,
                                holdCount: Option[Int] = Some(0),
                                fixedFields: Map[String, FixedField] = Map(),
                                varFields: List[VarField] = Nil
  ): SierraItemData =
    SierraItemData(
      id = id,
      location = location,
      copyNo = copyNo,
      holdCount = holdCount,
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
