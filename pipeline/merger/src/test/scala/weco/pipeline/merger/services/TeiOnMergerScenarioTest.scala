package weco.pipeline.merger.services

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.{CollectionPath, Work, WorkState}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.pipeline.merger.fixtures.FeatureTestSugar

// We'll eventually fold these tests into the base MergerScenarioTest
// once the TEI works are rich enough for the public
class TeiOnMergerScenarioTest
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with FeatureTestSugar
    with SourceWorkGenerators {
  val merger = MergerManager.teiOnMergerManager

  Scenario("A Tei and a Sierra digital and a sierra physical work are merged") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the Sierra works are redirected to the tei")
    outcome.getMerged(digitalSierra) should beRedirectedTo(teiWork)
    outcome.getMerged(physicalSierra) should beRedirectedTo(teiWork)

    And("the tei work has the Sierra works' items")
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf digitalSierra.data.items
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items

    And("the tei work has the Sierra works' identifiers")
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf physicalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf digitalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)
  }

  Scenario("A Tei with internal works and a Calm are merged") {
    Given("a Tei and a Calm record")
    val calmWork = calmIdentifiedWork().collectionPath(CollectionPath("a/b/c"))
    val firstInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val secondInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("2"))

    val teiWork = teiIdentifiedWork()
      .collectionPath(CollectionPath("tei_id"))
      .title("A tei work")
      .internalWorks(List(firstInternalWork, secondInternalWork))

    When("the works are merged")

    val works = List(calmWork, teiWork)
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the Cal work is redirected to the tei")
    outcome.getMerged(calmWork) should beRedirectedTo(teiWork)

    And("the tei work has the Calm work collectionPath")
    outcome
      .getMerged(teiWork)
      .data
      .collectionPath shouldBe calmWork.data.collectionPath

    And("the tei inner works have the calm collectionPath prepended")
    outcome
      .getMerged(firstInternalWork)
      .data
      .collectionPath shouldBe Some(CollectionPath(
      s"${calmWork.data.collectionPath.get.path}/${firstInternalWork.data.collectionPath.get.path}"))
    outcome
      .getMerged(secondInternalWork)
      .data
      .collectionPath shouldBe Some(CollectionPath(
      s"${calmWork.data.collectionPath.get.path}/${secondInternalWork.data.collectionPath.get.path}"))
  }

  Scenario(
    "A Tei with internal works and a Sierra digital and a sierra physical work are merged") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val firstInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val secondInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("2"))

    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(firstInternalWork, secondInternalWork))

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the Sierra works are redirected to the tei")
    outcome.getMerged(digitalSierra) should beRedirectedTo(teiWork)
    outcome.getMerged(physicalSierra) should beRedirectedTo(teiWork)

    And("the tei work has the Sierra works' items")
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf digitalSierra.data.items
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items

    And("the tei work has the Sierra works' identifiers")
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf physicalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf digitalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)

    And("the internal tei works are returned")
    outcome.resultWorks contains firstInternalWork
    outcome.resultWorks contains secondInternalWork
    outcome.getMerged(firstInternalWork) shouldNot beRedirectedTo(teiWork)
    outcome.getMerged(secondInternalWork) shouldNot beRedirectedTo(teiWork)

    And("the tei internal works contain the sierra item")
    outcome
      .getMerged(firstInternalWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items
    outcome
      .getMerged(secondInternalWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items

    And("the tei internal works retain their collectionsPath")
    outcome
      .getMerged(firstInternalWork)
      .data
      .collectionPath shouldBe firstInternalWork.data.collectionPath
    outcome
      .getMerged(secondInternalWork)
      .data
      .collectionPath shouldBe secondInternalWork.data.collectionPath
  }

  Scenario("A Tei work passes through unchanged") {
    Given("a Tei")
    val internalWork1 = teiIdentifiedWork()
    val internalWork2 = teiIdentifiedWork()
    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(internalWork1, internalWork2))

    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork)))

    Then("the tei work should be a TEI work")
    outcome.getMerged(teiWork) shouldBe teiWork

    And("the the tei inner works should be returned")
    outcome.getMerged(internalWork1) shouldBe updateInternalWork(
      internalWork1,
      teiWork)
    outcome.getMerged(internalWork2) shouldBe updateInternalWork(
      internalWork2,
      teiWork)
  }

  Scenario(
    "CollectionPath is prepended to internal tei works if the work is not merged") {
    Given("a Tei")
    val internalWork1 = teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val internalWork2 = teiIdentifiedWork().collectionPath(CollectionPath("2"))
    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(internalWork1, internalWork2))
      .collectionPath(CollectionPath("id"))

    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork)))

    Then("the tei work should be a TEI work")
    val expectedInternalWork1 = updateInternalWork(internalWork1, teiWork)
      .collectionPath(CollectionPath("id/1"))
    val expectedInternalWork2 = updateInternalWork(internalWork2, teiWork)
      .collectionPath(CollectionPath("id/2"))

    outcome.getMerged(internalWork1) shouldBe expectedInternalWork1
    outcome.getMerged(internalWork2) shouldBe expectedInternalWork2
    outcome.getMerged(teiWork) shouldBe teiWork.internalWorks(
      List(expectedInternalWork1, expectedInternalWork2))
  }

  Scenario("A TEI work, a Calm work, a Sierra work and a METS work") {
    Given("a TEI work")
    import weco.json.JsonUtil._
    val teiWork = fromJson[Work.Visible[WorkState.Identified]](
      """{
        |    "version" : 0,
        |    "data" : {
        |      "title" : "Medical Epitome",
        |      "otherIdentifiers" : [ ],
        |      "alternativeTitles" : [ ],
        |      "format" : {
        |        "id" : "h",
        |        "label" : "Archives and manuscripts"
        |      },
        |      "description" : "Paul of Aegina's Medical Epitome",
        |      "subjects" : [ ],
        |      "genres" : [ ],
        |      "contributors" : [ ],
        |      "production" : [ ],
        |      "languages" : [
        |        {
        |          "id" : "grc",
        |          "label" : "Greek, Ancient (to 1453)"
        |        }
        |      ],
        |      "notes" : [ ],
        |      "items" : [ ],
        |      "holdings" : [ ],
        |      "collectionPath" : {
        |        "path" : "MS_MSL_114"
        |      },
        |      "imageData" : [ ],
        |      "workType" : "Standard"
        |    },
        |    "state" : {
        |      "sourceIdentifier" : {
        |        "identifierType" : {
        |          "id" : "tei-manuscript-id"
        |        },
        |        "ontologyType" : "Work",
        |        "value" : "MS_MSL_114"
        |      },
        |      "canonicalId" : "ggge7hh2",
        |      "sourceModifiedTime" : "2021-07-07T14:51:47Z",
        |      "mergeCandidates" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "u94bmv2z",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "sierra-system-number"
        |              },
        |              "ontologyType" : "Work",
        |              "value" : "b18764459"
        |            },
        |            "otherIdentifiers" : [ ]
        |          },
        |          "reason" : "Bnumber present in TEI file"
        |        }
        |      ],
        |      "internalWorkStubs" : [ ]
        |    },
        |    "redirectSources" : [ ],
        |    "type" : "Visible"
        |  }""".stripMargin
    ).get

    val sierraWork = fromJson[Work[WorkState.Identified]]("""{
                                                  |    "version" : 692,
                                                  |    "data" : {
                                                  |      "title" : "The Works of Paulus Aegineta",
                                                  |      "otherIdentifiers" : [
                                                  |        {
                                                  |          "identifierType" : {
                                                  |            "id" : "sierra-identifier"
                                                  |          },
                                                  |          "ontologyType" : "Work",
                                                  |          "value" : "1876445"
                                                  |        },
                                                  |        {
                                                  |          "identifierType" : {
                                                  |            "id" : "wellcome-digcode"
                                                  |          },
                                                  |          "ontologyType" : "Work",
                                                  |          "value" : "digwms"
                                                  |        },
                                                  |        {
                                                  |          "identifierType" : {
                                                  |            "id" : "wellcome-digcode"
                                                  |          },
                                                  |          "ontologyType" : "Work",
                                                  |          "value" : "diggreek"
                                                  |        }
                                                  |      ],
                                                  |      "alternativeTitles" : [ ],
                                                  |      "format" : {
                                                  |        "id" : "h",
                                                  |        "label" : "Archives and manuscripts"
                                                  |      },
                                                  |      "description" : "<p><p>This fine manuscript is in good condition throughout, except for some water-stains on the last 10 folios. It bears no title-page, nor is the copyist known. Folio 1 has a text in two columns that has no relation to the body of the manuscript, and there are some marginal notes on folios 195-197; otherwise the whole is the work of one writer. The manuscript contains the whole of the seven books of Paulus Aegineta, and it must be assigned to the first half of the fifteenth century. It is one of the most valuable Greek texts in the collection, but it does not appear to have been collated by previous editors.</p><p>1. ff. 1r-v. Untitled text not connected to body of manuscript. <p>2. ff. 2-19v. Book i., treating of hygiene generally, including pregnancy, diseases of children, baths, foods, regimen, sleep, etc.</p><p>3. ff. 19v-36. Book ii., treating of fevers.</p><p>4. ff. 36v-88. Book iii., Local affections dealt with in topical order titfrom the top of the head to the soles of the feet.</p><p>5. ff. 88v-108. Book iv., External affections not peculiar to any particular part of the body; intestinal worms.</p><p>6. ff. 108v-118. Book v., Wounds, bites of men and animals, hydrophobia, and poisons. In the manuscript, this book is erroneously called the sixth.</p><p>7. ff. 118v-151. Book vi., treating of surgery, fractures and dislocations. Note: one page has been missed out in foliation, between f.128v and f.129r. <p>8. ff. 151v-197v. Book vii., The properties of drugs and composition of medicines; weights and measures.</p> <p>9. ff.197v-198v. Untitled text in different hands.</p>",
                                                  |      "physicalDescription" : "198 folios<br/>Folio. 30 × 23 cm. Tooled leather (18th cent.) binding, rebacked.",
                                                  |      "subjects" : [
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Pregnancy",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Pregnancy",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Child Welfare",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Child Welfare",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Hygiene",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Hygiene",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Sleep",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Sleep",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Diet",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Diet",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Fever",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Fever",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Parasites",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Parasites",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Disease",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Disease",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Wounds and Injuries",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Wounds and Injuries",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Bites and Stings",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Bites and Stings",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Poisons",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Poisons",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "General Surgery",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "General Surgery",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Fractures",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Fractures",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Joint Dislocations",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Joint Dislocations",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Pharmacology",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Pharmacology",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Pharmaceutical Preparations",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Pharmaceutical Preparations",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Weights and Measures",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Weights and Measures",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Life Style",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Life Style",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        },
                                                  |        {
                                                  |          "id" : {
                                                  |            "type" : "Unidentifiable"
                                                  |          },
                                                  |          "label" : "Manuscripts, Greek",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Manuscripts, Greek",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        }
                                                  |      ],
                                                  |      "genres" : [
                                                  |        {
                                                  |          "label" : "Archives",
                                                  |          "concepts" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Archives",
                                                  |              "type" : "Concept"
                                                  |            }
                                                  |          ]
                                                  |        }
                                                  |      ],
                                                  |      "contributors" : [ ],
                                                  |      "production" : [
                                                  |        {
                                                  |          "label" : "Early 15th century",
                                                  |          "places" : [ ],
                                                  |          "agents" : [ ],
                                                  |          "dates" : [
                                                  |            {
                                                  |              "id" : {
                                                  |                "type" : "Unidentifiable"
                                                  |              },
                                                  |              "label" : "Early 15th century",
                                                  |              "range" : {
                                                  |                "from" : "1400-01-01T00:00:00Z",
                                                  |                "to" : "1439-12-31T23:59:59.999999999Z",
                                                  |                "label" : "Early 15th century"
                                                  |              }
                                                  |            }
                                                  |          ]
                                                  |        }
                                                  |      ],
                                                  |      "languages" : [
                                                  |        {
                                                  |          "id" : "eng",
                                                  |          "label" : "English"
                                                  |        }
                                                  |      ],
                                                  |      "notes" : [
                                                  |        {
                                                  |          "noteType" : {
                                                  |            "id" : "location-of-original",
                                                  |            "label" : "Location of original"
                                                  |          },
                                                  |          "contents" : "Wellcome Library; GB."
                                                  |        },
                                                  |        {
                                                  |          "noteType" : {
                                                  |            "id" : "ownership-note",
                                                  |            "label" : "Ownership note"
                                                  |          },
                                                  |          "contents" : "On flyleaf: \"Ex Bibliotheca Askeviana. P. ii. Art. 404. J. SIMS.\""
                                                  |        },
                                                  |        {
                                                  |          "noteType" : {
                                                  |            "id" : "terms-of-use",
                                                  |            "label" : "Terms of use"
                                                  |          },
                                                  |          "contents" : "Images are supplied for private research only at the Archivist's discretion. Please note that material may be unsuitable for copying on conservation grounds. Researchers who wish to publish material must seek copyright permission from the copyright owner."
                                                  |        },
                                                  |        {
                                                  |          "noteType" : {
                                                  |            "id" : "location-of-duplicates",
                                                  |            "label" : "Location of duplicates"
                                                  |          },
                                                  |          "contents" : "This material has been digitised and can be freely accessed online through the Wellcome Library catalogue."
                                                  |        },
                                                  |        {
                                                  |          "noteType" : {
                                                  |            "id" : "publication-note",
                                                  |            "label" : "Publications note"
                                                  |          },
                                                  |          "contents" : \"\"\"This manuscript is described in detail by Petros Bouras-Vallianatos in <a href="http://dx.doi.org/10.1017/mdh.2015.6" target="_blank"><i>Medical History</i> <b>59</b> (2015), pp.275-326</a>.\"\"\"
                                                  |        }
                                                  |      ],
                                                  |      "items" : [
                                                  |        {
                                                  |          "id" : {
                                                  |            "canonicalId" : "ne45wbr7",
                                                  |            "sourceIdentifier" : {
                                                  |              "identifierType" : {
                                                  |                "id" : "sierra-system-number"
                                                  |              },
                                                  |              "ontologyType" : "Item",
                                                  |              "value" : "i17540823"
                                                  |            },
                                                  |            "otherIdentifiers" : [
                                                  |              {
                                                  |                "identifierType" : {
                                                  |                  "id" : "sierra-identifier"
                                                  |                },
                                                  |                "ontologyType" : "Item",
                                                  |                "value" : "1754082"
                                                  |              }
                                                  |            ],
                                                  |            "type" : "Identified"
                                                  |          },
                                                  |          "locations" : [
                                                  |            {
                                                  |              "locationType" : {
                                                  |                "id" : "closed-stores"
                                                  |              },
                                                  |              "label" : "Closed stores",
                                                  |              "accessConditions" : [
                                                  |                {
                                                  |                  "method" : {
                                                  |                    "type" : "OnlineRequest"
                                                  |                  },
                                                  |                  "status" : {
                                                  |                    "type" : "Open"
                                                  |                  }
                                                  |                }
                                                  |              ],
                                                  |              "type" : "PhysicalLocation"
                                                  |            }
                                                  |          ]
                                                  |        }
                                                  |      ],
                                                  |      "holdings" : [ ],
                                                  |      "imageData" : [ ],
                                                  |      "workType" : "Standard"
                                                  |    },
                                                  |    "state" : {
                                                  |      "sourceIdentifier" : {
                                                  |        "identifierType" : {
                                                  |          "id" : "sierra-system-number"
                                                  |        },
                                                  |        "ontologyType" : "Work",
                                                  |        "value" : "b18764459"
                                                  |      },
                                                  |      "canonicalId" : "u94bmv2z",
                                                  |      "sourceModifiedTime" : "2021-09-27T03:17:13Z",
                                                  |      "mergeCandidates" : [
                                                  |        {
                                                  |          "id" : {
                                                  |            "canonicalId" : "ej2bwuar",
                                                  |            "sourceIdentifier" : {
                                                  |              "identifierType" : {
                                                  |                "id" : "calm-record-id"
                                                  |              },
                                                  |              "ontologyType" : "Work",
                                                  |              "value" : "95216d9a-a7d2-44b5-ac35-87213ec720af"
                                                  |            },
                                                  |            "otherIdentifiers" : [ ]
                                                  |          },
                                                  |          "reason" : "Calm/Sierra harvest"
                                                  |        }
                                                  |      ],
                                                  |      "internalWorkStubs" : [ ]
                                                  |    },
                                                  |    "redirectSources" : [ ],
                                                  |    "type" : "Visible"
                                                  |  }""".stripMargin).get

    val metsWork = fromJson[Work[WorkState.Identified]]("""{
                                                          |    "version" : 1,
                                                          |    "data" : {
                                                          |      "title" : "[The Works of Paulus Aegineta] Untitled text in two columns that bears no relation to the body of the manuscript. Book i., treating of hygiene generally, including pregnancy, diseases of children, baths, foods, regimen, sleep, etc. Book ii., treating of fevers. Book iii., Local affections dealt with in topical order from the top of the head to the soles of the feet. Book iv., External affections not peculiar to any particular part of the body; intestinal worms. Book v., Wounds, bites of men and animals, hydrophobia, and poisons. In the manuscript, this book is erroneously called the sixth. Book vi., treating of surgery, fractures and dislocations. Book vii., The properties of drugs and composition of medicines; weights and measures. Untitled text in different hands.",
                                                          |      "otherIdentifiers" : [ ],
                                                          |      "alternativeTitles" : [ ],
                                                          |      "subjects" : [ ],
                                                          |      "genres" : [ ],
                                                          |      "contributors" : [ ],
                                                          |      "thumbnail" : {
                                                          |        "url" : "https://iiif.wellcomecollection.org/thumbs/b18764459_ms_114_0001.JP2/full/!200,200/0/default.jpg",
                                                          |        "locationType" : {
                                                          |          "id" : "thumbnail-image"
                                                          |        },
                                                          |        "license" : {
                                                          |          "id" : "pdm"
                                                          |        },
                                                          |        "accessConditions" : [ ],
                                                          |        "type" : "DigitalLocation"
                                                          |      },
                                                          |      "production" : [ ],
                                                          |      "languages" : [ ],
                                                          |      "notes" : [ ],
                                                          |      "items" : [
                                                          |        {
                                                          |          "id" : {
                                                          |            "type" : "Unidentifiable"
                                                          |          },
                                                          |          "locations" : [
                                                          |            {
                                                          |              "url" : "https://iiif.wellcomecollection.org/presentation/v2/b18764459",
                                                          |              "locationType" : {
                                                          |                "id" : "iiif-presentation"
                                                          |              },
                                                          |              "license" : {
                                                          |                "id" : "pdm"
                                                          |              },
                                                          |              "accessConditions" : [
                                                          |                {
                                                          |                  "method" : {
                                                          |                    "type" : "ViewOnline"
                                                          |                  },
                                                          |                  "status" : {
                                                          |                    "type" : "Open"
                                                          |                  }
                                                          |                }
                                                          |              ],
                                                          |              "type" : "DigitalLocation"
                                                          |            }
                                                          |          ]
                                                          |        }
                                                          |      ],
                                                          |      "holdings" : [ ],
                                                          |      "imageData" : [],
                                                          |      "workType" : "Standard"
                                                          |    },
                                                          |    "state" : {
                                                          |      "sourceIdentifier" : {
                                                          |        "identifierType" : {
                                                          |          "id" : "mets"
                                                          |        },
                                                          |        "ontologyType" : "Work",
                                                          |        "value" : "b18764459"
                                                          |      },
                                                          |      "canonicalId" : "w7f24nn4",
                                                          |      "sourceModifiedTime" : "2019-09-14T07:32:01.211Z",
                                                          |      "mergeCandidates" : [
                                                          |        {
                                                          |          "id" : {
                                                          |            "canonicalId" : "u94bmv2z",
                                                          |            "sourceIdentifier" : {
                                                          |              "identifierType" : {
                                                          |                "id" : "sierra-system-number"
                                                          |              },
                                                          |              "ontologyType" : "Work",
                                                          |              "value" : "b18764459"
                                                          |            },
                                                          |            "otherIdentifiers" : [ ]
                                                          |          },
                                                          |          "reason" : "METS work"
                                                          |        }
                                                          |      ],
                                                          |      "internalWorkStubs" : [ ]
                                                          |    },
                                                          |    "invisibilityReasons" : [
                                                          |      {
                                                          |        "type" : "MetsWorksAreNotVisible"
                                                          |      }
                                                          |    ],
                                                          |    "type" : "Invisible"
                                                          |  }""".stripMargin).get

    val calmWork = fromJson[Work[WorkState.Identified]]("""{
                                                          |    "version" : 5,
                                                          |    "data" : {
                                                          |      "title" : "The Works of Paulus Aegineta",
                                                          |      "otherIdentifiers" : [
                                                          |        {
                                                          |          "identifierType" : {
                                                          |            "id" : "calm-ref-no"
                                                          |          },
                                                          |          "ontologyType" : "Work",
                                                          |          "value" : "MSMSL114"
                                                          |        },
                                                          |        {
                                                          |          "identifierType" : {
                                                          |            "id" : "calm-altref-no"
                                                          |          },
                                                          |          "ontologyType" : "Work",
                                                          |          "value" : "MS.MSL.114"
                                                          |        }
                                                          |      ],
                                                          |      "alternativeTitles" : [ ],
                                                          |      "format" : {
                                                          |        "id" : "h",
                                                          |        "label" : "Archives and manuscripts"
                                                          |      },
                                                          |      "description" : \"\"\"<p>This fine manuscript is in good condition throughout, except for some water-stains on the last 10 folios. It bears no title-page, nor is the copyist known. Folio 1 has a text in two columns that has no relation to the body of the manuscript, and there are some marginal notes on folios 195-197; otherwise the whole is the work of one writer. The manuscript contains the whole of the seven books of Paulus Aegineta, and it must be assigned to the first half of the fifteenth century. It is one of the most valuable Greek texts in the collection, but it does not appear to have been collated by previous editors.</p>
                                                          |
                                                          |<p>1. ff. 1r-v. Untitled text not connected to body of manuscript.
                                                          |
                                                          |</p><p>2. ff. 2-19v. Book i., treating of hygiene generally, including pregnancy, diseases of children, baths, foods, regimen, sleep, etc.</p>
                                                          |
                                                          |<p>3. ff. 19v-36. Book ii., treating of fevers.</p>
                                                          |
                                                          |<p>4. ff. 36v-88. Book iii., Local affections dealt with in topical order titfrom the top of the head to the soles of the feet.</p>
                                                          |
                                                          |<p>5. ff. 88v-108. Book iv., External affections not peculiar to any particular part of the body; intestinal worms.</p>
                                                          |
                                                          |<p>6. ff. 108v-118. Book v., Wounds, bites of men and animals, hydrophobia, and poisons. In the manuscript, this book is erroneously called the sixth.</p>
                                                          |
                                                          |<p>7. ff. 118v-151. Book vi., treating of surgery, fractures and dislocations. Note: one page has been missed out in foliation, between f.128v and f.129r.
                                                          |
                                                          |</p><p>8. ff. 151v-197v. Book vii., The properties of drugs and composition of medicines; weights and measures.</p>
                                                          |
                                                          |<p>9. ff.197v-198v. Untitled text in different hands.</p>\"\"\",
                                                          |      "physicalDescription" : "198 folios Folio. 30 × 23 cm. Tooled leather (18th cent.) binding, rebacked.",
                                                          |      "subjects" : [],
                                                          |      "genres" : [ ],
                                                          |      "contributors" : [ ],
                                                          |      "production" : [],
                                                          |      "languages" : [ ],
                                                          |      "notes" : [],
                                                          |      "items" : [
                                                          |        {
                                                          |          "id" : {
                                                          |            "type" : "Unidentifiable"
                                                          |          },
                                                          |          "locations" : [
                                                          |            {
                                                          |              "locationType" : {
                                                          |                "id" : "closed-stores"
                                                          |              },
                                                          |              "label" : "Closed stores",
                                                          |              "accessConditions" : [
                                                          |                {
                                                          |                  "method" : {
                                                          |                    "type" : "NotRequestable"
                                                          |                  },
                                                          |                  "status" : {
                                                          |                    "type" : "Open"
                                                          |                  }
                                                          |                }
                                                          |              ],
                                                          |              "type" : "PhysicalLocation"
                                                          |            }
                                                          |          ]
                                                          |        }
                                                          |      ],
                                                          |      "holdings" : [ ],
                                                          |      "collectionPath" : {
                                                          |        "path" : "MSMSL114",
                                                          |        "label" : "MS.MSL.114"
                                                          |      },
                                                          |      "referenceNumber" : "MS.MSL.114",
                                                          |      "imageData" : [ ],
                                                          |      "workType" : "Standard"
                                                          |    },
                                                          |    "state" : {
                                                          |      "sourceIdentifier" : {
                                                          |        "identifierType" : {
                                                          |          "id" : "calm-record-id"
                                                          |        },
                                                          |        "ontologyType" : "Work",
                                                          |        "value" : "95216d9a-a7d2-44b5-ac35-87213ec720af"
                                                          |      },
                                                          |      "canonicalId" : "ej2bwuar",
                                                          |      "sourceModifiedTime" : "2021-03-02T13:38:19Z",
                                                          |      "mergeCandidates" : [ ],
                                                          |      "internalWorkStubs" : [ ]
                                                          |    },
                                                          |    "redirectSources" : [ ],
                                                          |    "type" : "Visible"
                                                          |  }""".stripMargin).get

    val outcome = merger.applyMerge(List(teiWork, sierraWork, metsWork, calmWork).map(Some(_)))

    outcome.getMerged(sierraWork) should beRedirectedTo(teiWork)
    outcome.getMerged(metsWork) should beRedirectedTo(teiWork)
    outcome.getMerged(calmWork) should beRedirectedTo(teiWork)

    val teiMergedIdentifiers =
      outcome
        .getMerged(teiWork)
        .data
        .otherIdentifiers

    Then("the TEI work gets all the CALM and Sierra identifiers")
    teiMergedIdentifiers should contain allElementsOf calmWork.data.otherIdentifiers :+ calmWork.state.sourceIdentifier
    teiMergedIdentifiers should contain allElementsOf sierraWork.data.otherIdentifiers :+ sierraWork.state.sourceIdentifier

    And("it has no METS identifier")
    teiMergedIdentifiers.filter(_.identifierType == IdentifierType.METS) shouldBe empty

    And("it only has two items (one physical, one digital)")
    val teiItems =
      outcome
        .getMerged(teiWork)
        .data
        .items

    teiItems should contain allElementsOf sierraWork.data.items
    teiItems should contain allElementsOf metsWork.data.items
    teiItems should contain noElementsOf calmWork.data.items
  }

  private def updateInternalWork(
    internalWork: Work.Visible[WorkState.Identified],
    teiWork: Work.Visible[WorkState.Identified]) =
    internalWork
      .copy(version = teiWork.version)
      .mapState(state =>
        state.copy(sourceModifiedTime = teiWork.state.sourceModifiedTime))
}
