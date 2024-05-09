package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.{Subfield, VarField}

class SierraLetteringTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("ignores records with the wrong MARC field") {
    assertFindsCorrectLettering(
      varFields = List(
        createVarFieldWith(
          marcTag = "300",
          indicator2 = "6",
          subfields = List(
            Subfield(tag = "a", content = "Alas, ailments are annoying")
          )
        )
      ),
      expectedLettering = None
    )
  }

  it("ignores records with the right MARC field but wrong indicator 2") {
    assertFindsCorrectLettering(
      varFields = List(
        createVarFieldWith(
          marcTag = "246",
          indicator2 = "7",
          subfields = List(
            Subfield(tag = "a", content = "Alas, ailments are annoying")
          )
        )
      ),
      expectedLettering = None
    )
  }

  it(
    "ignores records with the MARC field and 2nd indicator but wrong subfield"
  ) {
    assertFindsCorrectLettering(
      varFields = List(
        createVarFieldWith(
          marcTag = "246",
          indicator2 = "6",
          subfields = List(
            Subfield(
              tag = "b",
              content = "Belligerent beavers beneath a bridge"
            )
          )
        )
      ),
      expectedLettering = None
    )
  }

  it("passes through a single instance of 246 .6 $$a, if present") {
    assertFindsCorrectLettering(
      varFields = List(
        createVarFieldWith(
          marcTag = "246",
          indicator2 = "6",
          subfields = List(
            Subfield(
              tag = "a",
              content = "Crowded crows carry a chocolate crepe"
            )
          )
        )
      ),
      expectedLettering = Some("Crowded crows carry a chocolate crepe")
    )
  }

  it("joins multiple instances of 246 .6 $$a, if present") {
    assertFindsCorrectLettering(
      varFields = List(
        createVarFieldWith(
          marcTag = "246",
          indicator2 = "6",
          subfields = List(
            Subfield(tag = "a", content = "Daring dalmations dance with danger")
          )
        ),
        createVarFieldWith(
          marcTag = "246",
          indicator2 = "6",
          subfields = List(
            Subfield(
              tag = "a",
              content = "Enterprising eskimos exile every eagle"
            )
          )
        )
      ),
      expectedLettering = Some(
        "Daring dalmations dance with danger\n\nEnterprising eskimos exile every eagle"
      )
    )
  }

  describe("lettering for visual material") {
    it("uses both 246 .6 ǂa and 514 for visual material") {
      // This is based on b16529888
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("k")),
        varFields = List(
          VarField(
            marcTag = "514",
            subfields = List(
              Subfield(
                tag = "a",
                content =
                  "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
              )
            )
          ),
          VarField(
            marcTag = Some("246"),
            indicator2 = Some("6"),
            subfields = List(
              Subfield(
                tag = "a",
                content = "Le m\u00e9decin et la garde malade. H.D. ..."
              )
            )
          )
        )
      )

      SierraLettering(bibData) shouldBe Some(
        "Le m\u00e9decin et la garde malade. H.D. ...\n\nLettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
      )
    }

    it("only uses 246 .6 ǂa for non-visual material") {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("not-k")),
        varFields = List(
          VarField(
            marcTag = "514",
            subfields = List(
              Subfield(
                tag = "a",
                content =
                  "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
              )
            )
          ),
          VarField(
            marcTag = Some("246"),
            indicator2 = Some("6"),
            subfields = List(
              Subfield(
                tag = "a",
                content = "Le m\u00e9decin et la garde malade. H.D. ..."
              )
            )
          )
        )
      )

      SierraLettering(bibData) shouldBe Some(
        "Le m\u00e9decin et la garde malade. H.D. ..."
      )
    }
  }

  private def assertFindsCorrectLettering(
    varFields: List[VarField],
    expectedLettering: Option[String]
  ) = {
    val bibData = createSierraBibDataWith(varFields = varFields)
    SierraLettering(bibData) shouldBe expectedLettering
  }
}
