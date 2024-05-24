package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import weco.catalogue.internal_model.work.Relation
import weco.catalogue.internal_model.work.WorkType.Series
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcParentsTest extends AnyFunSpec with Matchers with LoneElement {
  describe(
    "Extracting relation information from MARC fields 440, 490, 773 & 830"
  ) {
    info("https://www.loc.gov/marc/bibliographic/bd4xx.html")
    info("https://www.loc.gov/marc/bibliographic/bd773.html")
    info("https://www.loc.gov/marc/bibliographic/bd830.html")

    val testCases = Table(
      ("MarcSubfields", "expectedTitle"),
      (
        List(
          MarcField(
            marcTag = "440",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 440ǂa"))
          )
        ),
        List("A title from 440ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "490",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 490ǂa"))
          )
        ),
        List("A title from 490ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "773",
            subfields =
              List(MarcSubfield(tag = "t", content = "A title from 773ǂt"))
          )
        ),
        List("A title from 773ǂt")
      ),
      (
        List(
          MarcField(
            marcTag = "773",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 773ǂa"))
          )
        ),
        List("A title from 773ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "773",
            subfields =
              List(MarcSubfield(tag = "s", content = "A title from 773ǂs"))
          )
        ),
        List("A title from 773ǂs")
      ),
      (
        List(
          MarcField(
            marcTag = "830",
            subfields =
              List(MarcSubfield(tag = "t", content = "A title from 830ǂt"))
          )
        ),
        List("A title from 830ǂt")
      ),
      (
        List(
          MarcField(
            marcTag = "830",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 830ǂa"))
          )
        ),
        List("A title from 830ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "830",
            subfields = List(
              MarcSubfield(tag = "t", content = "A title from 830ǂt"),
              MarcSubfield(tag = "a", content = "A title from 830ǂa")
            )
          )
        ),
        List("A title from 830ǂt")
      ),
      (
        List(
          MarcField(
            marcTag = "830",
            subfields = List(
              MarcSubfield(tag = "a", content = "A title from 830ǂa"),
              MarcSubfield(tag = "s", content = "A title from 830ǂs")
            )
          )
        ),
        List("A title from 830ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "440",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 440ǂa"))
          ),
          MarcField(
            marcTag = "490",
            subfields =
              List(MarcSubfield(tag = "a", content = "A title from 490ǂa"))
          )
        ),
        List("A title from 440ǂa", "A title from 490ǂa")
      ),
      (
        List(
          MarcField(
            marcTag = "830",
            subfields = List(MarcSubfield(tag = "x", content = "9999-9999"))
          )
        ),
        List()
      )
    )

    def createRelationWithTitle(title: String): Relation =
      Relation(
        id = None,
        title = Some(title),
        collectionPath = None,
        workType = Series,
        depth = 0,
        numChildren = 0,
        numDescendents = 0
      )

    /** This test uses the same test data as SierraParentsTest, modified to
      * create MarcField instances instead of VarField instances.
      */
    it(
      "constructs relations appropriate for the MARC fields 440, 490, 773, 830"
    ) {
      forAll(testCases) {
        case (testMarcFields, expectedTitles) =>
          MarcParents(
            record = MarcTestRecord(fields = testMarcFields)
          ) shouldBe expectedTitles.map(
            createRelationWithTitle
          )
      }
    }
  }
}
