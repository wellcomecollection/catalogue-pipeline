package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor2
import uk.ac.wellcome.models.work.internal.Collection
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}

class SierraCalmRefNoTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val refNo = "SAMWF/J/1/15"
  val altRefNo = "SA/MWF/J/1/15"

  /**
    * `RefNo` is stored in marcField `905` subfield `a`
    * {
    *   "fieldTag": "y",
    *   "marcTag": "905",
    *   "ind1": " ",
    *   "ind2": " ",
    *   "subfields": [
    *     {
    *       "tag": "a",
    *       "content": "SAMWF/J/1/15"
    *     }
    *   ]
    * }
    *
    * `AltRefNo is stored in marcField `001` `contents`
    * {
    *   "fieldTag": "o",
    *   "marcTag": "001",
    *   "ind1": " ",
    *   "ind2": " ",
    *     "content": "SA/MWF/J.15"
    *   }
    *
    **/
  it("Extracts the 905 field") {
    val examples = Table(
      ("-varfields-", "-collection-"),
      (createVarField(refNo, "905", "a") :: Nil, Some(Collection(None, refNo))),
      (createVarField(refNo, "509", "a") :: Nil, None),
      (createVarField(refNo, "905", "b") :: Nil, None)
    )

    check(examples)
  }

  it("labels the collection using 100 field") {
    val examples = Table(
      ("-varfields-", "-collection-"),
      (
        List(
          createVarField(refNo, "905", "a"),
          create100Varfield(altRefNo)
        ),
        Some(Collection(Some(altRefNo), refNo))),
      (
        List(
          createVarField(refNo, "509", "a"),
          create100Varfield(altRefNo)
        ),
        None)
    )

    check(examples)
  }

  private def check(examples: TableFor2[List[VarField], Option[Collection]]) =
    forAll(examples) {
      (varFields: List[VarField], collection: Option[Collection]) =>
        {
          getCalmRefNo(varFields) shouldBe collection
        }
    }

  private def getCalmRefNo(varFields: List[VarField]) =
    SierraCalmRefNo(
      createSierraBibNumber,
      createSierraBibDataWith(varFields = varFields))

  private def create100Varfield(content: String) =
    createVarFieldWith("100", content = Some(content))

  private def createVarField(
    content: String,
    tag: String,
    subfieldTag: String,
    indicator2: String = "1",
  ) =
    createVarFieldWith(
      tag,
      indicator2,
      MarcSubfield(tag = subfieldTag, content = content) :: Nil)
}
