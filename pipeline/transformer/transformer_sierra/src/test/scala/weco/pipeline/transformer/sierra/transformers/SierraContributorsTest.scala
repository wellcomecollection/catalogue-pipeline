package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  ContributorMatchers,
  HasIdMatchers
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inspectors, LoneElement}
import weco.catalogue.internal_model.identifiers.IdState.Identifiable

class SierraContributorsTest
    extends AnyFunSpec
    with Matchers
    with ContributorMatchers
    with ConceptMatchers
    with HasIdMatchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks
    with Inspectors
    with LoneElement {
  import weco.pipeline.transformer.marc_common.OntologyTypeOps._

  it("gets an empty contributor list from empty bib data") {
    SierraContributors(createSierraBibDataWith(varFields = Nil)) shouldBe Nil
  }

  it("extracts a mixture of Person, Organisation and Meeting contributors") {
    val varFields = List(
      VarField(
        marcTag = "100",
        subfields = List(
          Subfield(tag = "a", content = "Sarah the soybean")
        )
      ),
      VarField(
        marcTag = "100",
        subfields = List(
          Subfield(tag = "a", content = "Sam the squash,"),
          Subfield(tag = "c", content = "Sir")
        )
      ),
      VarField(
        marcTag = "110",
        subfields = List(
          Subfield(tag = "a", content = "Spinach Solicitors")
        )
      ),
      VarField(
        marcTag = "700",
        subfields = List(
          Subfield(tag = "a", content = "Sebastian the sugarsnap")
        )
      ),
      VarField(
        marcTag = "710",
        subfields = List(
          Subfield(tag = "a", content = "Shallot Swimmers")
        )
      ),
      VarField(
        marcTag = "711",
        subfields = List(
          Subfield(tag = "a", content = "Sammys meet the Sammys"),
          Subfield(tag = "c", content = "at Sammys")
        )
      )
    )
    val contributors =
      SierraContributors(createSierraBibDataWith(varFields = varFields))
    contributors.size shouldBe 6
    all(contributors.slice(0, 2)) shouldBe primary
    all(contributors.slice(3, 5)) shouldNot be(primary)

    List(
      (
        (agent: AbstractAgent[IdState.Unminted]) => agent shouldBe a[Person[_]],
        "Person",
        "Sarah the soybean"
      ),
      (
        (agent: AbstractAgent[IdState.Unminted]) => agent shouldBe a[Person[_]],
        "Person",
        "Sam the squash, Sir"
      ),
      (
        (agent: AbstractAgent[IdState.Unminted]) =>
          agent shouldBe a[Organisation[_]],
        "Organisation",
        "Spinach Solicitors"
      ),
      (
        (agent: AbstractAgent[IdState.Unminted]) => agent shouldBe a[Person[_]],
        "Person",
        "Sebastian the sugarsnap"
      ),
      (
        (agent: AbstractAgent[IdState.Unminted]) =>
          agent shouldBe a[Organisation[_]],
        "Organisation",
        "Shallot Swimmers"
      ),
      (
        (agent: AbstractAgent[IdState.Unminted]) =>
          agent shouldBe a[Meeting[_]],
        "Meeting",
        "Sammys meet the Sammys at Sammys"
      )
    ).zip(contributors).map {
      case ((assertType, ontologyType, label), contributor) =>
        assertType(
          contributor.agent.asInstanceOf[AbstractAgent[IdState.Unminted]]
        )
        contributor.agent should have(
          'label(label),
          sourceIdentifier(
            ontologyType = ontologyType,
            identifierType = IdentifierType.LabelDerived,
            value = label.toLowerCase()
          )
        )
    }
  }

  describe("Person") {
    it("extracts and combines only subfields ǂa ǂb ǂc ǂd for the label") {
      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = "Charles Emmanuel"),
            Subfield(tag = "b", content = "III,"),
            Subfield(tag = "c", content = "King of Sardinia,"),
            Subfield(tag = "d", content = "1701-1773")
          )
        ),
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Charles Emmanuel"),
            Subfield(tag = "b", content = "IV,"),
            Subfield(tag = "c", content = "King of Sardinia,"),
            Subfield(tag = "d", content = "1796-1802")
          )
        )
      )
      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      all(contributors) should have('roles(Nil))
      all(contributors.map(_.agent)) shouldBe a[Person[_]]
      val List(c1, c2) = contributors
      c1.agent.label shouldBe "Charles Emmanuel III, King of Sardinia, 1701-1773"
      c1 shouldBe primary
      c2.agent.label shouldBe "Charles Emmanuel IV, King of Sardinia, 1796-1802"
      c2 shouldNot be(primary)
    }

    it(
      "combines subfield ǂt with ǂa ǂb ǂc ǂd and creates an Agent, not a Person from MARC field 100 / 700"
    ) {
      // Based on https://search.wellcomelibrary.org/iii/encore/record/C__Rb1159639?marcData=Y
      // as retrieved on 4 February 2019.
      val varFields = List(
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Shakespeare, William,"),
            Subfield(tag = "d", content = "1564-1616."),
            Subfield(tag = "t", content = "Hamlet.")
          )
        )
      )

      val bibData = createSierraBibDataWith(varFields = varFields)
      val List(contributor) = SierraContributors(bibData)

      contributor.agent should have(
        'label("Shakespeare, William, 1564-1616. Hamlet.")
      )
      contributor.agent shouldBe an[Agent[IdState.Identifiable]]
    }

    it(
      "gets the name from MARC tags 100 and 700 subfield ǂa in the right order"
    ) {
      val name1 = "Alfie the Artichoke"
      val name2 = "Alison the Apple"
      val name3 = "Archie the Aubergine"

      // The correct ordering is "everything from 100 first, then 700", and
      // we deliberately pick an ordering that's different from that for
      // the MARC fields, so we can check it really is applying this rule.
      val varFields = List(
        VarField(
          marcTag = "700",
          subfields = List(Subfield(tag = "a", content = name2))
        ),
        VarField(
          marcTag = "100",
          subfields = List(Subfield(tag = "a", content = name1))
        ),
        VarField(
          marcTag = "700",
          subfields = List(Subfield(tag = "a", content = name3))
        )
      )

      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      all(contributors) should have('roles(Nil))
      all(contributors.map(_.agent)) shouldBe a[Person[_]]
      contributors.map(_.agent.label) shouldBe List(name1, name2, name3)
      all(contributors.tail) shouldNot be(primary)
      contributors.head shouldBe primary
    }

    it("gets roles from subfield ǂe") {
      val name = "Violet the Vanilla"
      val role1 = "spice"
      val role2 = "flavour"

      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "e", content = role1),
            Subfield(tag = "e", content = role2)
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent shouldBe a[Person[_]]
      contributor.agent should have('label(name))
      contributor shouldBe primary
      contributor should have(
        roles(List(role1, role2))
      )
    }

    it("gets a role from subfield ǂj") {
      // This is based on b1202594x, as retrieved 8 September 2022
      val varFields = List(
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Zurbarán, Francisco de,"),
            Subfield(tag = "d", content = "1598-1664,"),
            Subfield(tag = "j", content = "Follower of")
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      contributor should have(roles(List("Follower of")))
    }

    it("gets roles from both subfield ǂe and ǂj") {
      // This is a hypothetical test case, at time of writing (8 September 2022),
      // there are no instances of 700 with both subfields.
      val varFields = List(
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "A made-up leader"),
            Subfield(tag = "j", content = "Follower of"),
            Subfield(tag = "e", content = "Disciple of")
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      contributor should have(roles(List("Follower of", "Disciple of")))
    }

    it("gets the full form of a name from subfield ǂq") {
      // This is based on b11941820
      val varFields = List(
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Faujas-de-St.-Fond,"),
            Subfield(tag = "c", content = "cit."),
            Subfield(tag = "q", content = "(Barthélemey),"),
            Subfield(tag = "d", content = "1741-1819"),
            Subfield(tag = "e", content = "dedicatee.")
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent.label shouldBe "Faujas-de-St.-Fond, cit. (Barthélemey), 1741-1819"
    }

    it("gets an identifier from subfield ǂ0") {
      val name = "Ivan the ivy"
      val lcshCode = "nlcsh7101607"

      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode)
          )
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent should have(
        'label(name),
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Person",
          value = lcshCode
        )
      )
    }

    it(
      "combines identifiers with inconsistent spacing/punctuation from subfield ǂ0"
    ) {
      val name = "Wanda the watercress"
      val lcshCodeCanonical = "nlcsh2055034"
      val lcshCode1 = "nlcsh 2055034"
      val lcshCode2 = "  nlcsh2055034 "
      val lcshCode3 = " nlc sh 2055034"

      // Based on an example from a real record; see Sierra b3017492.
      val lcshCode4 = "nlcsh 2055034.,"

      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode1),
            Subfield(tag = "0", content = lcshCode2),
            Subfield(tag = "0", content = lcshCode3),
            Subfield(tag = "0", content = lcshCode4)
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent should have(
        'label(name),
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Person",
          value = lcshCodeCanonical
        )
      )
      contributor.roles shouldBe Nil
      contributor.agent shouldBe a[Person[_]]
    }

    it(
      "uses a label-derived identifier if there are multiple distinct identifiers in subfield ǂ0"
    ) {
      val name = "Darren the Dill"
      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = "nlcsh9069541"),
            Subfield(tag = "0", content = "nlcsh3384149")
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      contributor.agent shouldBe a[Person[_]]
      contributor.agent should have(
        'label(name),
        labelDerivedPersonId(name.toLowerCase)
      )
    }

    it("normalises Person contributor labels") {
      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(Subfield(tag = "a", content = "George,"))
        ),
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Sebastian,")
          )
        )
      )

      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributors.size shouldBe 2
      all(contributors) should have('roles(Nil))
      all(contributors.map(_.agent)) shouldBe a[Person[_]]
      contributors.map(_.agent.label) shouldBe List("George", "Sebastian")
      contributors.head shouldBe primary
      contributors(1) shouldNot be(primary)
    }
  }

  describe("Organisation") {
    it("gets the name from MARC tag 110 subfield ǂa") {
      val name = "Ona the orache"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(Subfield(tag = "a", content = name))
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent shouldBe an[Organisation[_]]
      contributor.agent should have(
        'label(name)
      )
    }

    it(
      "combines only subfields ǂa ǂb ǂc ǂd (multiples of) with spaces from MARC field 110 / 710"
    ) {
      // Based on https://search.wellcomelibrary.org/iii/encore/record/C__Rb1000984
      // as retrieved from 25 April 2019
      val name =
        "IARC Working Group on the Evaluation of the Carcinogenic Risk of Chemicals to Man."
      val subordinateUnit = "Meeting"
      val date = "1972 :"
      val place = "Lyon, France"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "b", content = subordinateUnit),
            Subfield(tag = "d", content = date),
            Subfield(tag = "c", content = place),
            Subfield(tag = "n", content = "  79125097")
          )
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent shouldBe an[Organisation[_]]
      contributor.agent should have(
        'label(
          "IARC Working Group on the Evaluation of the Carcinogenic Risk of Chemicals to Man. Meeting 1972 : Lyon, France"
        )
      )
    }

    it(
      "gets the name from MARC tags 110 and 710 subfield ǂa in the right order"
    ) {
      val name1 = "Mary the mallow"
      val name2 = "Mike the mashua"
      val name3 = "Mickey the mozuku"

      // The correct ordering is "everything from 110 first, then 710", and
      // we deliberately pick an ordering that's different from that for
      // the MARC fields, so we can check it really is applying this rule.
      val varFields = List(
        VarField(
          marcTag = "710",
          subfields = List(Subfield(tag = "a", content = name2))
        ),
        VarField(
          marcTag = "110",
          subfields = List(Subfield(tag = "a", content = name1))
        ),
        VarField(
          marcTag = "710",
          subfields = List(Subfield(tag = "a", content = name3))
        )
      )
      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      all(contributors) should have('roles(Nil))
      all(contributors.map(_.agent)) shouldBe a[Organisation[_]]
      contributors.map(_.agent.label) shouldBe List(name1, name2, name3)
      contributors.head shouldBe primary
      all(contributors.tail) shouldNot be(primary)
    }

    it("gets the roles from subfield ǂe") {
      val name = "Terry the turmeric"
      val role1 = "dye"
      val role2 = "colouring"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "e", content = role1),
            Subfield(tag = "e", content = role2)
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent shouldBe an[Organisation[_]]
      contributor.agent should have('label(name))
      contributor should have(
        roles(List(role1, role2))
      )
    }

    it("gets an identifier from subfield ǂ0") {
      val name = "Gerry the Garlic"
      val lcshCode = "nlcsh7212"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode)
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent should have(
        'label(name),
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Organisation",
          value = lcshCode
        )
      )
      contributor.roles shouldBe Nil
      contributor.agent shouldBe an[Organisation[_]]
    }

    it("gets an identifier with inconsistent spacing from subfield ǂ0") {
      val name = "Charlie the chive"
      val lcshCodeCanonical = "nlcsh6791210"
      val lcshCode1 = "nlcsh 6791210"
      val lcshCode2 = "  nlcsh6791210 "
      val lcshCode3 = " nlc sh 6791210"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode1),
            Subfield(tag = "0", content = lcshCode2),
            Subfield(tag = "0", content = lcshCode3)
          )
        )
      )

      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributor.agent should have(
        'label(name),
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Organisation",
          value = lcshCodeCanonical
        )
      )
      contributor.roles shouldBe Nil
      contributor.agent shouldBe an[Organisation[_]]
    }

    it(
      "uses a label-derived identifier if there are multiple distinct identifiers in subfield ǂ0"
    ) {
      val name = "Luke the lime"
      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = "nlcsh3349285"),
            Subfield(tag = "0", content = "nlcsh9059917")
          )
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = varFields))

      contributor.agent shouldBe an[Organisation[_]]
      contributor.agent should have(
        'label(name),
        labelDerivedOrganisationId(name.toLowerCase)
      )
    }

    it("normalises Organisation contributor labels") {
      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(Subfield(tag = "a", content = "The organisation,"))
        ),
        VarField(
          marcTag = "710",
          subfields =
            List(Subfield(tag = "a", content = "Another organisation,"))
        )
      )

      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributors should have size 2
      all(contributors) should have('roles(Nil))
      all(contributors.map(_.agent)) shouldBe a[Organisation[_]]
      contributors.map(_.agent.label) shouldBe List(
        "The organisation",
        "Another organisation"
      )
      contributors.head shouldBe primary
      contributors(1) shouldNot be(primary)
    }
  }

  // This is based on transformer failures we saw in October 2018 --
  // records 3069865, 3069866, 3069867, 3069872 all had empty instances of
  // the 110 field.
  it("returns an empty list if subfield ǂa is missing") {
    val varFields = List(
      VarField(
        marcTag = "100",
        subfields = List(
          Subfield(tag = "e", content = "")
        )
      )
    )
    SierraContributors(
      createSierraBibDataWith(varFields = varFields)
    ) shouldBe Nil
  }

  describe("Meeting") {
    it("gets the name from MARC tag 111 subfield ǂa") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(Subfield(tag = "a", content = "Big meeting"))
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = List(varField)))
      contributor.agent should have('label("Big meeting"))
      contributor.agent shouldBe a[Meeting[_]]
    }

    it("gets the name from MARC tag 711 subfield ǂa") {
      val varField = VarField(
        marcTag = "711",
        subfields = List(Subfield(tag = "a", content = "Big meeting"))
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = List(varField)))
      contributor.agent should have('label("Big meeting"))
      contributor.agent shouldBe a[Meeting[_]]
      contributor shouldNot be(primary)

    }

    it("combinies subfields ǂa, ǂc, ǂd and ǂt with spaces") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "1"),
          Subfield(tag = "b", content = "not used"),
          Subfield(tag = "c", content = "2"),
          Subfield(tag = "d", content = "3"),
          Subfield(tag = "t", content = "4")
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = List(varField)))
      contributor.agent should have('label("1 2 3 4"))
      contributor shouldBe primary
    }

    it("gets the roles from subfield ǂj") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "label"),
          Subfield(tag = "e", content = "not a role"),
          Subfield(tag = "j", content = "1st role"),
          Subfield(tag = "j", content = "2nd role")
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = List(varField)))
      contributor.agent should have('label("label"))
      contributor should have(
        roles(List("1st role", "2nd role"))
      )
    }

    it("gets an identifier from subfield ǂ0") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "label"),
          Subfield(tag = "0", content = "456")
        )
      )
      val List(contributor) =
        SierraContributors(createSierraBibDataWith(varFields = List(varField)))
      contributor.agent should have(
        'label("label"),
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Meeting",
          value = "456"
        )
      )
      contributor.roles shouldBe Nil
    }
  }

  // This is based on the MARC record for b24541758, as of 23 January 2021
  // The same contributor was listed in both 100 and 700 fields
  it("deduplicates contributors") {
    val varFields = List(
      VarField(
        marcTag = "100",
        subfields = List(
          Subfield(tag = "a", content = "Steele, Richard,"),
          Subfield(tag = "c", content = "Sir,"),
          Subfield(tag = "d", content = "1672-1729.")
        )
      ),
      VarField(
        marcTag = "700",
        subfields = List(
          Subfield(tag = "a", content = "Steele, Richard,"),
          Subfield(tag = "c", content = "Sir,"),
          Subfield(tag = "d", content = "1672-1729.")
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)
    val contributor = SierraContributors(bibData).loneElement

    contributor should have(
      'roles(Nil)
    )
    contributor.agent shouldBe a[Person[_]]
    contributor shouldBe primary
    contributor.agent should have(
      'label("Steele, Richard, Sir, 1672-1729.")
    )

  }

  it("includes subfield ǂn") {
    // This example is based on b23042059
    val varFields = List(
      VarField(
        marcTag = "700",
        subfields = List(
          Subfield(tag = "a", content = "Brewer, George."),
          Subfield(
            tag = "t",
            content = "Essays after the manner of Goldsmith,"
          ),
          Subfield(tag = "n", content = "No. 1-22.")
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)
    val List(contributor) = SierraContributors(bibData)
    contributor shouldNot be(primary)
    contributor.agent should have(
      'label("Brewer, George. Essays after the manner of Goldsmith, No. 1-22.")
    )
  }

  it("includes subfield ǂp") {
    // This example is based on b14582855
    val varFields = List(
      VarField(
        marcTag = "700",
        subfields = List(
          Subfield(tag = "a", content = "Hippocrates."),
          Subfield(tag = "t", content = "Epistolae."),
          Subfield(
            tag = "p",
            content = "Ad Ptolemaeum regem de hominis fabrica."
          ),
          Subfield(tag = "l", content = "Latin."),
          Subfield(tag = "f", content = "1561."),
          Subfield(
            tag = "0",
            content = "n  79005643"
          ) // This identifier is for Hippocrates only
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraContributors(bibData) shouldBe List(
      Contributor(
        agent = Agent(
          id = IdState.Identifiable(
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LCNames,
              value = "n79005643",
              ontologyType = "Agent"
            )
          ),
          label =
            "Hippocrates. Epistolae. Ad Ptolemaeum regem de hominis fabrica. Latin."
        ),
        roles = List.empty,
        primary = false
      )
    )
  }

  it("removes trailing punctuation from the contribution role") {
    // This is based on the MARC record for b28975005
    val varFields = List(
      VarField(
        marcTag = "700",
        subfields = List(
          Subfield(tag = "a", content = "Nurse, Paul,"),
          Subfield(tag = "d", content = "1949-"),
          Subfield(tag = "e", content = "writer of introduction.")
        )
      )
    )

    val List(contributor) =
      SierraContributors(createSierraBibDataWith(varFields = varFields))

    contributor should have(
      roles(List("writer of introduction"))
    )
  }

  describe("Harmonising contributor types") {
    it(
      "replaces vague agent ontology types with more specific ones with matching ids"
    ) {
      forAll(
        Table(
          ("vagueType", "specificType", "specificAgentConstructor"),
          (
            "Agent",
            "Person",
            (id: IdState.Identifiable, label: String) =>
              new Person[IdState.Identifiable](id = id, label = label)
          ),
          (
            "Agent",
            "Organisation",
            (id: IdState.Identifiable, label: String) =>
              new Organisation[IdState.Identifiable](id = id, label = label)
          ),
          (
            "Agent",
            "Meeting",
            (id: IdState.Identifiable, label: String) =>
              new Meeting[IdState.Identifiable](id = id, label = label)
          )
        )
      ) {
        (
          vagueType,
          specificType,
          specificAgentConstructor: (
            Identifiable,
            String
          ) => AbstractAgent[Identifiable]
        ) =>
          val vagueContributor = Contributor(
            agent = new Agent(
              label = "Maimonides, in his work on Logic",
              id = Identifiable(
                SourceIdentifier(
                  IdentifierType.LCSubjects,
                  vagueType,
                  "sh00000000"
                )
              )
            ),
            roles = Nil
          )
          val specificContributor = Contributor(
            agent = specificAgentConstructor(
              Identifiable(
                SourceIdentifier(
                  IdentifierType.LCSubjects,
                  specificType,
                  "sh00000000"
                )
              ),
              "Maimonides"
            ),
            roles = Nil
          )
          val contributors =
            List(vagueContributor, specificContributor).harmoniseOntologyTypes
          // Both subjects should be represented in the output list.  They have different labels, so are different objects.
          contributors.length shouldBe 2
          val commonClass = contributors(1).agent.getClass
          forAll(contributors) {
            contributor =>
              contributor.agent.getClass shouldBe commonClass
              contributor.agent.id.allSourceIdentifiers.head.ontologyType shouldBe specificType
          }
      }
    }
  }

  it("collapses contributors who are no longer unique") {

    val vagueContributor = Contributor(
      agent = new Agent(
        label = "Maimonides",
        id = Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Agent",
            "sh00000000"
          )
        )
      ),
      roles = Nil
    )
    val specificContributor1 = Contributor(
      agent = Person(
        Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Person",
            "sh00000000"
          )
        ),
        "Maimonides"
      ),
      roles = Nil
    )
    val vagueContributor2 = Contributor(
      agent = Organisation(
        Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Agent",
            "sh00000000"
          )
        ),
        "Maimonides"
      ),
      roles = Nil
    )
    val contributors =
      List(
        vagueContributor,
        specificContributor1,
        vagueContributor2
      ).harmoniseOntologyTypes
    // They all had the same label before, so they are no longer unique having harmonised their types
    contributors.length shouldBe 1

    contributors.head.agent.id.allSourceIdentifiers.head.ontologyType shouldBe "Person"
  }

  it("chooses the first specific concept if there are more than one") {

    val vagueContributor = Contributor(
      agent = new Agent(
        label = "Maimonides, in his work on Logic",
        id = Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Agent",
            "sh00000000"
          )
        )
      ),
      roles = Nil
    )

    val specificContributor1 = Contributor(
      agent = Person(
        Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Person",
            "sh00000000"
          )
        ),
        "Maimonides"
      ),
      roles = Nil
    )

    val specificContributor2 = Contributor(
      agent = Organisation(
        Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Agent",
            "sh00000000"
          )
        ),
        "Maimonides and his chums"
      ),
      roles = Nil
    )
    val contributors =
      List(
        specificContributor1,
        specificContributor2,
        vagueContributor
      ).harmoniseOntologyTypes

    contributors.length shouldBe 3

    forAll(contributors) {
      contributor =>
        contributor.agent
          .asInstanceOf[Person[IdState.Identifiable]]
          .id
          .sourceIdentifier
          .ontologyType shouldBe "Person"
    }
  }
}
