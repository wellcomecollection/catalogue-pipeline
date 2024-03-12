package weco.pipeline.transformer.sierra.data

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class BibDataAsMarcRecordTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with SierraMarcDataConversions {

  it("turns Varfields into MarcFields") {
    val bibData = createSierraBibDataWith(varFields =
      List(
        VarField(
          marcTag = Some("999"),
          subfields = List(Subfield(tag = "a", content = "banana"))
        ),
        VarField(
          marcTag = Some("123"),
          subfields = List(Subfield(tag = "a", content = "coypu"))
        )
      )
    )
    val marcRecord = bibDataToMarcRecord(bibData)
    marcRecord.fields should have length 2
    marcRecord
      .fieldsWithTags("123")
      .head shouldBe a[MarcField]
    marcRecord.fields.head.subfields.head should have(
      'tag("a"),
      'content("banana")
    )

  }

  it("ignores VarFields without a marcTag") {
    val bibData = createSierraBibDataWith(varFields =
      List(
        VarField(
          marcTag = Some("999"),
          subfields = List(Subfield(tag = "a", content = "topper"))
        ),
        VarField(
          marcTag = None,
          subfields = List(Subfield(tag = "a", content = "wayfarer"))
        ),
        VarField(fieldTag = "invalid", content = "laser"),
        VarField(
          marcTag = Some("123"),
          subfields = List(Subfield(tag = "a", content = "coypu"))
        )
      )
    )
    val marcRecord = bibDataToMarcRecord(bibData)
    marcRecord.fields should have length 2
    val field123 = marcRecord.fieldsWithTags("123").head
    field123.subfields.head should have(
      'tag("a"),
      'content("coypu")
    )

  }

}
