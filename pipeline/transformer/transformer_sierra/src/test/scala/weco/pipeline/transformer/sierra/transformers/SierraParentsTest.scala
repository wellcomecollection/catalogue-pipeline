package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.catalogue.internal_model.work.Relation
import weco.catalogue.internal_model.work.WorkType.Series
import weco.fixtures.RandomGenerators
import weco.sierra.models.marc.{Subfield, VarField}
import weco.sierra.generators.SierraDataGenerators

class SierraParentsTest
    extends AnyFunSpec
    with Matchers
    with RandomGenerators
    with SierraDataGenerators {

  private val testCases = Table(
    ("subfields", "expectedTitle"),
    (
      List(
        VarField(
          marcTag = "440",
          subfields = List(Subfield(tag = "a", content = "A title from 440ǂa"))
        )
      ),
      List("A title from 440ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "490",
          subfields = List(Subfield(tag = "a", content = "A title from 490ǂa"))
        )
      ),
      List("A title from 490ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "773",
          subfields = List(Subfield(tag = "t", content = "A title from 773ǂt"))
        )
      ),
      List("A title from 773ǂt")
    ),
    (
      List(
        VarField(
          marcTag = "773",
          subfields = List(Subfield(tag = "a", content = "A title from 773ǂa"))
        )
      ),
      List("A title from 773ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "773",
          subfields = List(Subfield(tag = "s", content = "A title from 773ǂs"))
        )
      ),
      List("A title from 773ǂs")
    ),
    (
      List(
        VarField(
          marcTag = "830",
          subfields = List(Subfield(tag = "t", content = "A title from 830ǂt"))
        )
      ),
      List("A title from 830ǂt")
    ),
    (
      List(
        VarField(
          marcTag = "830",
          subfields = List(Subfield(tag = "a", content = "A title from 830ǂa"))
        )
      ),
      List("A title from 830ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "830",
          subfields = List(
            Subfield(tag = "t", content = "A title from 830ǂt"),
            Subfield(tag = "a", content = "A title from 830ǂa")
          )
        )
      ),
      List("A title from 830ǂt")
    ),
    (
      List(
        VarField(
          marcTag = "830",
          subfields = List(
            Subfield(tag = "a", content = "A title from 830ǂa"),
            Subfield(tag = "s", content = "A title from 830ǂs")
          )
        )
      ),
      List("A title from 830ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "440",
          subfields = List(Subfield(tag = "a", content = "A title from 440ǂa"))
        ),
        VarField(
          marcTag = "490",
          subfields = List(Subfield(tag = "a", content = "A title from 490ǂa"))
        )
      ),
      List("A title from 440ǂa", "A title from 490ǂa")
    ),
    (
      List(
        VarField(
          marcTag = "830",
          subfields = List(Subfield(tag = "x", content = "9999-9999"))
        )
      ),
      List()
    ),
  )

  private def createRelationWithTitle(title: String): Relation =
    Relation(
      id = None,
      title = Some(title),
      collectionPath = None,
      workType = Series,
      depth = 0,
      numChildren = 0,
      numDescendents = 0
    )

  it("constructs relations appropriate for the MARC fields 440, 490, 773, 830") {
    forAll(testCases) {
      case (testVarFields, expectedTitles) =>
        val bibData = createSierraBibDataWith(
          varFields = testVarFields
        )

        SierraParents(bibData = bibData) shouldBe expectedTitles.map(
          createRelationWithTitle
        )
    }
  }
}
