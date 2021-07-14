package weco.catalogue.source_model.sierra

import org.scalatest.TryValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.sierra.identifiers.SierraItemNumber
import weco.catalogue.source_model.sierra.marc.{FixedField, MarcSubfield, VarField}
import weco.json.JsonUtil._

class SierraItemDataTest extends AnyFunSpec with Matchers with TryValues {
  it("decodes itemData") {
    val itemDataJson =
      s"""
         |{
         |  "id": "1146055",
         |  "deleted": false,
         |  "suppressed": false,
         |  "holdCount": 1,
         |  "fixedFields": {
         |    "57": {
         |      "label": "BIB HOLD",
         |      "value": "false"
         |    }
         |  },
         |  "varFields": [
         |    {
         |      "fieldTag": "a",
         |      "marcTag": "949",
         |      "ind1": "0",
         |      "ind2": "0",
         |      "subfields": [
         |        {
         |          "tag": "1",
         |          "content": "STAX"
         |        },
         |        {
         |          "tag": "2",
         |          "content": "sepam"
         |        }
         |      ]
         |    }
         |  ]
         |}""".stripMargin

    fromJson[SierraItemData](itemDataJson).get shouldBe SierraItemData(
      id = SierraItemNumber("1146055"),
      deleted = false,
      suppressed = false,
      copyNo = None,
      holdCount = Some(1),
      location = None,
      fixedFields = Map(
        "57" -> FixedField(
          label="BIB HOLD",
          value="false",
          display=None
        )
      ),
      varFields = List(VarField(
        content = None,
        marcTag = Some("949"),
        fieldTag = Some("a"),
        indicator1 = Some("0"),
        indicator2 = Some("0"),
        subfields = List(
          MarcSubfield("1","STAX"),
          MarcSubfield("2","sepam")
        ))
      )
    )
  }
}
