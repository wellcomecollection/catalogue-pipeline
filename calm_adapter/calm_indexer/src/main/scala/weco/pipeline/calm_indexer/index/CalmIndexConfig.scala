package weco.pipeline.calm_indexer.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.catalogue.internal_model.index.IndexConfigFields
import weco.elasticsearch.IndexConfig

object CalmIndexConfig extends IndexConfigFields {
  def apply(): IndexConfig =
    IndexConfig(
      properties(
        fieldNames.map(fieldName =>
          textKeywordField(
            name = fieldName,
            textFieldName = "english",
            analyzerName = "english"
          )
        )
      )
        .dynamic(DynamicMapping.Strict),
      Analysis(analyzers = List())
    )

  private val fieldNames = Seq(
    "ACCESS",
    "ALLIED_MATERIALS",
    "AccessCategory",
    "AccessConditions",
    "AccessStatus",
    "Access_Licence",
    "Accruals",
    "Acquisition",
    "AdminHistory",
    "AltRefNo",
    "Appraisal",
    "Arrangement",
    "Bnumber",
    "CONSERVATIONREQUIRED",
    "CONTENT",
    "CONTEXT",
    "CatalogueStatus",
    "ClosedUntil",
    "Condition",
    "ConservationPriority",
    "ConservationStatus",
    "Copies",
    "Copyright",
    "CountryCode",
    "Created",
    "Creator",
    "CreatorName",
    "Credits",
    "CustodialHistory",
    "Data_Import_Landing",
    "Data_Import_Landing_2",
    "Date",
    "Description",
    "Digitised",
    "Document",
    "Engine",
    "ExitNote",
    "Extent",
    "Format",
    "Format_Details",
    "FuelSystem",
    "Hazard",
    "HazardsNote",
    "IDENTITY",
    "Ignition",
    "IssRecvd",
    "Language",
    "Level",
    "Link_To_Digitised",
    "Location",
    "MISC_Reference",
    "Material",
    "Metadata_Licence",
    "Modified",
    "Modifier",
    "Notes",
    "Ordering_Instructions",
    "Originals",
    "PackingNote",
    "PreviousNumbers",
    "PublnNote",
    "RCN",
    "RecordID",
    "RecordType",
    "RefNo",
    "RegNo",
    "RelatedMaterial",
    "RepositoryCode",
    "Sources_Guides_Used",
    "Title",
    "Transmission",
    "UserDate1",
    "UserPeriod2",
    "UserText1",
    "UserText2",
    "UserText3",
    "UserText4",
    "UserText5",
    "UserText6",
    "UserText7",
    "UserText8",
    "UserText9",
    "UserWrapped1",
    "UserWrapped2",
    "UserWrapped3",
    "UserWrapped4",
    "UserWrapped5",
    "UserWrapped6",
    "UserWrapped7",
    "UserWrapped8",
    "VehicleDetails",
    "Wheels"
  )
}
