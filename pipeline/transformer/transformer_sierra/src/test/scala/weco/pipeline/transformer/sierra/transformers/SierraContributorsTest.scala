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
  HasIdMatchers
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraContributorsTest
    extends AnyFunSpec
    with Matchers
    with ConceptMatchers
    with HasIdMatchers
    with SierraDataGenerators {

  it("gets an empty contributor list from empty bib data") {
    transformAndCheckContributors(
      varFields = List(),
      expectedContributors = List())
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
      ),
    )

    val expectedContributors = List(
      Contributor(Person("Sarah the soybean"), roles = Nil),
      Contributor(Person("Sam the squash, Sir"), roles = Nil),
      Contributor(Organisation("Spinach Solicitors"), roles = Nil),
      Contributor(Person("Sebastian the sugarsnap"), roles = Nil),
      Contributor(Organisation("Shallot Swimmers"), roles = Nil),
      Contributor(Meeting("Sammys meet the Sammys at Sammys"), roles = Nil)
    )
    transformAndCheckContributors(
      varFields = varFields,
      expectedContributors = expectedContributors)
  }

  describe("Person") {
    it("extracts and combines only subfields $$a $$b $$c $$d for the label") {
      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = "Charles Emmanuel"),
            Subfield(tag = "b", content = "III,"),
            Subfield(tag = "c", content = "King of Sardinia,"),
            Subfield(tag = "d", content = "1701-1773"),
          )
        ),
        VarField(
          marcTag = "700",
          subfields = List(
            Subfield(tag = "a", content = "Charles Emmanuel"),
            Subfield(tag = "b", content = "IV,"),
            Subfield(tag = "c", content = "King of Sardinia,"),
            Subfield(tag = "d", content = "1796-1802"),
          )
        )
      )

      val expectedContributors = List(
        Contributor(
          Person(label = "Charles Emmanuel III, King of Sardinia, 1701-1773"),
          roles = Nil),
        Contributor(
          Person(label = "Charles Emmanuel IV, King of Sardinia, 1796-1802"),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "combines subfield $$t with $$a $$b $$c $$d and creates an Agent, not a Person from MARC field 100 / 700") {
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
      val contributors = SierraContributors(bibData)
      contributors should have size 1
      val contributor = contributors.head

      contributor.agent shouldBe Agent(
        "Shakespeare, William, 1564-1616. Hamlet.")
    }

    it(
      "gets the name from MARC tags 100 and 700 subfield $$a in the right order") {
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

      val expectedContributors = List(
        Contributor(Person(label = name1), roles = Nil),
        Contributor(Person(label = name2), roles = Nil),
        Contributor(Person(label = name3), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it("gets the roles from subfield $$e") {
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

      val expectedContributors = List(
        Contributor(
          agent = Person(label = name),
          roles = List(ContributionRole(role1), ContributionRole(role2))
        )
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
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
            Subfield(tag = "e", content = "dedicatee."),
          )
        )
      )

      val contributors =
        SierraContributors(createSierraBibDataWith(varFields = varFields))
      contributors should have size 1

      contributors.head.agent.label shouldBe "Faujas-de-St.-Fond, cit. (Barthélemey), 1741-1819"
    }

    it("gets an identifier from subfield $$0") {
      val name = "Ivan the ivy"
      val lcshCode = "lcsh7101607"

      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode)
          )
        )
      )

      val sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Person",
        value = lcshCode
      )

      val expectedContributors = List(
        Contributor(
          Person(label = name, id = IdState.Identifiable(sourceIdentifier)),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "combines identifiers with inconsistent spacing/punctuation from subfield $$0") {
      val name = "Wanda the watercress"
      val lcshCodeCanonical = "lcsh2055034"
      val lcshCode1 = "lcsh 2055034"
      val lcshCode2 = "  lcsh2055034 "
      val lcshCode3 = " lc sh 2055034"

      // Based on an example from a real record; see Sierra b3017492.
      val lcshCode4 = "lcsh 2055034.,"

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

      val sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Person",
        value = lcshCodeCanonical
      )

      val expectedContributors = List(
        Contributor(
          Person(label = name, id = IdState.Identifiable(sourceIdentifier)),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "does not identify the contributor if there are multiple distinct identifiers in subfield $$0") {
      val name = "Darren the Dill"
      val varFields = List(
        VarField(
          marcTag = "100",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = "lcsh9069541"),
            Subfield(tag = "0", content = "lcsh3384149")
          )
        )
      )

      val expectedContributors = List(
        Contributor(Person(name), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
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

      val expectedContributors = List(
        Contributor(Person(label = "George"), roles = Nil),
        Contributor(Person(label = "Sebastian"), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }
  }

  describe("Organisation") {
    it("gets the name from MARC tag 110 subfield $$a") {
      val name = "Ona the orache"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(Subfield(tag = "a", content = name))
        )
      )

      val expectedContributors = List(
        Contributor(Organisation(label = name), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "combines only subfields $$a $$b $$c $$d (multiples of) with spaces from MARC field 110 / 710") {
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

      val expectedContributors = List(
        Contributor(
          Organisation(
            label =
              "IARC Working Group on the Evaluation of the Carcinogenic Risk of Chemicals to Man. Meeting 1972 : Lyon, France"
          ),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "gets the name from MARC tags 110 and 710 subfield $$a in the right order") {
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

      val expectedContributors = List(
        Contributor(Organisation(label = name1), roles = Nil),
        Contributor(Organisation(label = name2), roles = Nil),
        Contributor(Organisation(label = name3), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it("gets the roles from subfield $$e") {
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

      val expectedContributors = List(
        Contributor(
          Organisation(label = name),
          roles = List(ContributionRole(role1), ContributionRole(role2))
        )
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it("gets an identifier from subfield $$0") {
      val name = "Gerry the Garlic"
      val lcshCode = "lcsh7212"

      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode)
          )
        )
      )

      val sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Organisation",
        value = lcshCode
      )

      val expectedContributors = List(
        Contributor(
          Organisation(
            label = name,
            id = IdState.Identifiable(sourceIdentifier)),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it("gets an identifier with inconsistent spacing from subfield $$0") {
      val name = "Charlie the chive"
      val lcshCodeCanonical = "lcsh6791210"
      val lcshCode1 = "lcsh 6791210"
      val lcshCode2 = "  lcsh6791210 "
      val lcshCode3 = " lc sh 6791210"

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

      val sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Organisation",
        value = lcshCodeCanonical
      )

      val expectedContributors = List(
        Contributor(
          Organisation(
            label = name,
            id = IdState.Identifiable(sourceIdentifier)),
          roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }

    it(
      "does not identify the contributor if there are multiple distinct identifiers in subfield $$0") {
      val name = "Luke the lime"
      val varFields = List(
        VarField(
          marcTag = "110",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = "lcsh3349285"),
            Subfield(tag = "0", content = "lcsh9059917")
          )
        )
      )

      val expectedContributors = List(
        Contributor(Organisation(label = name), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
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

      val expectedContributors = List(
        Contributor(Organisation(label = "The organisation"), roles = Nil),
        Contributor(Organisation(label = "Another organisation"), roles = Nil)
      )

      transformAndCheckContributors(
        varFields = varFields,
        expectedContributors = expectedContributors)
    }
  }

  // This is based on transformer failures we saw in October 2018 --
  // records 3069865, 3069866, 3069867, 3069872 all had empty instances of
  // the 110 field.
  it("returns an empty list if subfield $$a is missing") {
    val varFields = List(
      VarField(
        marcTag = "100",
        subfields = List(
          Subfield(tag = "e", content = "")
        )
      )
    )

    transformAndCheckContributors(
      varFields = varFields,
      expectedContributors = List())
  }

  describe("Meeting") {
    it("gets the name from MARC tag 111 subfield $$a") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(Subfield(tag = "a", content = "Big meeting"))
      )
      val contributor = Contributor(Meeting(label = "Big meeting"), roles = Nil)
      transformAndCheckContributors(List(varField), List(contributor))
    }

    it("gets the name from MARC tag 711 subfield $$a") {
      val varField = VarField(
        marcTag = "711",
        subfields = List(Subfield(tag = "a", content = "Big meeting"))
      )
      val contributor = Contributor(Meeting(label = "Big meeting"), roles = Nil)
      transformAndCheckContributors(List(varField), List(contributor))
    }

    it("combinies subfields $$a, $$c, $$d and $$t with spaces") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "1"),
          Subfield(tag = "b", content = "not used"),
          Subfield(tag = "c", content = "2"),
          Subfield(tag = "d", content = "3"),
          Subfield(tag = "t", content = "4"),
        )
      )
      val contributor = Contributor(Meeting(label = "1 2 3 4"), roles = Nil)
      transformAndCheckContributors(List(varField), List(contributor))
    }

    it("gets the roles from subfield $$j") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "label"),
          Subfield(tag = "e", content = "not a role"),
          Subfield(tag = "j", content = "1st role"),
          Subfield(tag = "j", content = "2nd role"),
        )
      )
      val contributor = Contributor(
        agent = Meeting(label = "label"),
        roles = List(ContributionRole("1st role"), ContributionRole("2nd role"))
      )
      transformAndCheckContributors(List(varField), List(contributor))
    }

    it("gets an identifier from subfield $$0") {
      val varField = VarField(
        marcTag = "111",
        subfields = List(
          Subfield(tag = "a", content = "label"),
          Subfield(tag = "0", content = "456")
        )
      )
      val sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Meeting",
        value = "456"
      )
      val contributor = Contributor(
        Meeting(label = "label", id = IdState.Identifiable(sourceIdentifier)),
        roles = Nil
      )
      transformAndCheckContributors(List(varField), List(contributor))
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

    SierraContributors(bibData) shouldBe List(
      Contributor(
        agent = Person(label = "Steele, Richard, Sir, 1672-1729."),
        roles = List.empty
      )
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
            content = "Essays after the manner of Goldsmith,"),
          Subfield(tag = "n", content = "No. 1-22.")
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraContributors(bibData) shouldBe List(
      Contributor(
        agent = Agent(
          label =
            "Brewer, George. Essays after the manner of Goldsmith, No. 1-22."
        ),
        roles = List.empty
      )
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
            content = "Ad Ptolemaeum regem de hominis fabrica."),
          Subfield(tag = "l", content = "Latin."),
          Subfield(tag = "f", content = "1561."),
          Subfield(tag = "0", content = "n  79005643"),
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
            "Hippocrates. Epistolae. Ad Ptolemaeum regem de hominis fabrica."
        ),
        roles = List.empty
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
          Subfield(tag = "e", content = "writer of introduction."),
        )
      ),
    )

    val contributors =
      SierraContributors(createSierraBibDataWith(varFields = varFields))
    contributors should have size 1

    contributors.head.roles shouldBe List(
      ContributionRole("writer of introduction"))
  }

  private def transformAndCheckContributors(
    varFields: List[VarField],
    expectedContributors: List[Contributor[IdState.Unminted]]
  ) = {
    val actualContributors = SierraContributors(
      createSierraBibDataWith(varFields = varFields))

    actualContributors.size shouldBe expectedContributors.size

    actualContributors.zip(expectedContributors).map {
      case (actualContributor, expectedContributor) =>
        val (expectedOntologyType, subjectHasId) =
          expectedContributor.agent match {
            case _: Person[IdState.Unminted]       => ("Person", false)
            case _: Organisation[IdState.Unminted] => ("Organisation", false)
            case _: Meeting[IdState.Unminted]      => ("Meeting", true)
            case _: Agent[IdState.Unminted]        => ("Agent", false)
            case other                             => fail(s"Contributor $other was of an unexpected type")
          }
        if (subjectHasId)
          actualContributor should have(
            sourceIdentifier(
              value = expectedContributor.agent.label,
              identifierType = IdentifierType.LabelDerived,
              ontologyType = expectedOntologyType
            )
          )

        actualContributor.agent should have(
          'label (expectedContributor.agent.label),
          sourceIdentifier(
            value = expectedContributor.agent.label,
            identifierType = IdentifierType.LabelDerived,
            ontologyType = expectedOntologyType
          )
        )
    }
  }
}
