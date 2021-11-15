package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.{MergeCandidate, Work}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators

// Tests here will eventually be folded into PlatformMergerTest.scala
// This allows us to test using the TeiOnMerger until we make that the default merger
class TeiOnMergerTest
    extends AnyFunSpec
    with SourceWorkGenerators
    with Matchers {

  it("merges a physical sierra with a tei") {
    val merger = PlatformMerger
    val physicalWork =
      sierraIdentifiedWork()
        .items(List(createIdentifiedPhysicalItem))
    val teiWork = teiIdentifiedWork().mergeCandidates(
      List(
        MergeCandidate(
          id = IdState.Identified(
            canonicalId = physicalWork.state.canonicalId,
            sourceIdentifier = physicalWork.state.sourceIdentifier
          ),
          reason = "Physical/digitised Sierra work"
        )
      )
    )

    val result = merger
      .merge(works = Seq(teiWork, physicalWork))
      .mergedWorksWithTime(now)
    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

    redirectedWorks should have size 1
    visibleWorks should have size 1

    visibleWorks.head.state.canonicalId shouldBe teiWork.state.canonicalId
  }

  it("copies the thumbnail to the inner works") {
    import weco.json.JsonUtil._
    import weco.catalogue.internal_model.Implicits._

    val teiWork = fromJson[Work[Identified]](
      """
        |{
        |    "version" : 2,
        |    "data" : {
        |      "title" : "Wellcome Malay 7",
        |      "otherIdentifiers" : [ ],
        |      "alternativeTitles" : [ ],
        |      "format" : {
        |        "id" : "h",
        |        "label" : "Archives and manuscripts"
        |      },
        |      "description" : "Catalogues of Malayan plants, fish, animals and snakes",
        |      "physicalDescription" : "Multiple manuscript parts collected in one volume.; Material: paper",
        |      "subjects" : [ ],
        |      "genres" : [ ],
        |      "contributors" : [ ],
        |      "production" : [ ],
        |      "languages" : [
        |        {
        |          "id" : "may",
        |          "label" : "Malay"
        |        }
        |      ],
        |      "notes" : [ ],
        |      "items" : [ ],
        |      "holdings" : [ ],
        |      "collectionPath" : {
        |        "path" : "Wellcome_Malay_7"
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
        |        "value" : "Wellcome_Malay_7"
        |      },
        |      "canonicalId" : "m7w39w9p",
        |      "sourceModifiedTime" : "2021-10-08T10:49:30Z",
        |      "mergeCandidates" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "kssjs5yh",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "sierra-system-number"
        |              },
        |              "ontologyType" : "Work",
        |              "value" : "b30172603"
        |            },
        |            "otherIdentifiers" : [ ]
        |          },
        |          "reason" : "Bnumber present in TEI file"
        |        }
        |      ],
        |      "internalWorkStubs" : [
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_1"
        |          },
        |          "canonicalId" : "a2amn373",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "Lists of plants, roots, woods, fibres, snakes, animals and insects.",
        |            "physicalDescription" : "Material: paper; 30 pages, divided into 3 pp. and 27 pp.; leaf dimensions: width 34 cm, height 41 cm; leaf dimensions: width 20.5 cm, height 34 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_1_Item_1"
        |          },
        |          "canonicalId" : "r3jcczed",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 1 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "eng",
        |                "label" : "English"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_1/Wellcome_Malay_7_Part_1_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_1_Item_2"
        |          },
        |          "canonicalId" : "srktqgqc",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 1 item 2",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              },
        |              {
        |                "id" : "eng",
        |                "label" : "English"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_1/Wellcome_Malay_7_Part_1_Item_2"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_2"
        |          },
        |          "canonicalId" : "yvewy4aw",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 2",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "Lists of plants (in Roman script) collected by Mr. Alvins in November and December, 1887. A letter from V. Jackson is included, commending the men he worked with.",
        |            "physicalDescription" : "Material: paper; 16 pages.; leaf dimensions: width 21 cm, height 34 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_2"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_2_Item_1"
        |          },
        |          "canonicalId" : "a7hy8wxn",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 2 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              },
        |              {
        |                "id" : "eng",
        |                "label" : "English"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_2/Wellcome_Malay_7_Part_2_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_3"
        |          },
        |          "canonicalId" : "hwf5yzq6",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 3",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "Lists of collection of timber, roots, and seeds.",
        |            "physicalDescription" : "Material: paper; 18 pages.; leaf dimensions: width 20.5 cm, height 33 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_3"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_3_Item_1"
        |          },
        |          "canonicalId" : "tqrw2fn8",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 3 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              },
        |              {
        |                "id" : "eng",
        |                "label" : "English"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_3/Wellcome_Malay_7_Part_3_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_4"
        |          },
        |          "canonicalId" : "wpbmyjhm",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 4",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "List of timber sent to Singapore for S. S. Rumbow.",
        |            "physicalDescription" : "Material: paper; 2 pages.; leaf dimensions: width 20.5 cm, height 33 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_4"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_4_Item_1"
        |          },
        |          "canonicalId" : "a5d92cj4",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 4 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_4/Wellcome_Malay_7_Part_4_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_5"
        |          },
        |          "canonicalId" : "qx9n4a9u",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 5",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "Names of fish, crabs, crayfish, shells.",
        |            "physicalDescription" : "Material: paper; 3 pages.; leaf dimensions: width 20.5 cm, height 33 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_5"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_5_Item_1"
        |          },
        |          "canonicalId" : "nze489rn",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 5 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_5/Wellcome_Malay_7_Part_5_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_6"
        |          },
        |          "canonicalId" : "hfbp7h27",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 6",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "List of plant names",
        |            "physicalDescription" : "Material: paper; 1 page.; leaf dimensions: width 20.5 cm, height 34 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_6"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_6_Item_1"
        |          },
        |          "canonicalId" : "dw9kqqhy",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 6 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_6/Wellcome_Malay_7_Part_6_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_7"
        |          },
        |          "canonicalId" : "n2zvsk48",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 7",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "description" : "Names of fish, shellfish, crabs etc",
        |            "physicalDescription" : "Material: paper; 3 pages.; leaf dimensions: width 20.5 cm, height 33 cm",
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [
        |              {
        |                "id" : "may",
        |                "label" : "Malay"
        |              }
        |            ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_7"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        },
        |        {
        |          "sourceIdentifier" : {
        |            "identifierType" : {
        |              "id" : "tei-manuscript-id"
        |            },
        |            "ontologyType" : "Work",
        |            "value" : "Wellcome_Malay_7_Part_7_Item_1"
        |          },
        |          "canonicalId" : "bvxsny5r",
        |          "workData" : {
        |            "title" : "Wellcome Malay 7 part 7 item 1",
        |            "otherIdentifiers" : [ ],
        |            "alternativeTitles" : [ ],
        |            "format" : {
        |              "id" : "h",
        |              "label" : "Archives and manuscripts"
        |            },
        |            "subjects" : [ ],
        |            "genres" : [ ],
        |            "contributors" : [ ],
        |            "production" : [ ],
        |            "languages" : [ ],
        |            "notes" : [ ],
        |            "items" : [ ],
        |            "holdings" : [ ],
        |            "collectionPath" : {
        |              "path" : "Wellcome_Malay_7/Wellcome_Malay_7_Part_7/Wellcome_Malay_7_Part_7_Item_1"
        |            },
        |            "imageData" : [ ],
        |            "workType" : "Standard"
        |          }
        |        }
        |      ]
        |    },
        |    "redirectSources" : [ ],
        |    "type" : "Visible"
        |  }
        |""".stripMargin).get

    val sierraWork = fromJson[Work[Identified]](
      """
        |{
        |    "version" : 2,
        |    "data" : {
        |      "title" : "[Catalogues of Malayan plants, fish, animals and snakes]",
        |      "otherIdentifiers" : [
        |        {
        |          "identifierType" : {
        |            "id" : "sierra-identifier"
        |          },
        |          "ontologyType" : "Work",
        |          "value" : "3017260"
        |        },
        |        {
        |          "identifierType" : {
        |            "id" : "wellcome-digcode"
        |          },
        |          "ontologyType" : "Work",
        |          "value" : "digmalay"
        |        }
        |      ],
        |      "alternativeTitles" : [
        |        "MS Malay 7"
        |      ],
        |      "format" : {
        |        "id" : "b",
        |        "label" : "Manuscripts"
        |      },
        |      "subjects" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "w5pajw3d",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "nlm-mesh"
        |              },
        |              "ontologyType" : "Subject",
        |              "value" : "D019021"
        |            },
        |            "otherIdentifiers" : [ ],
        |            "type" : "Identified"
        |          },
        |          "label" : "Natural History",
        |          "concepts" : [
        |            {
        |              "id" : {
        |                "type" : "Unidentifiable"
        |              },
        |              "label" : "Natural History",
        |              "type" : "Concept"
        |            }
        |          ]
        |        },
        |        {
        |          "id" : {
        |            "canonicalId" : "ks37g488",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "nlm-mesh"
        |              },
        |              "ontologyType" : "Subject",
        |              "value" : "D001901"
        |            },
        |            "otherIdentifiers" : [ ],
        |            "type" : "Identified"
        |          },
        |          "label" : "Botany",
        |          "concepts" : [
        |            {
        |              "id" : {
        |                "type" : "Unidentifiable"
        |              },
        |              "label" : "Botany",
        |              "type" : "Concept"
        |            }
        |          ]
        |        },
        |        {
        |          "id" : {
        |            "canonicalId" : "dtwzfg98",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "nlm-mesh"
        |              },
        |              "ontologyType" : "Subject",
        |              "value" : "D010946"
        |            },
        |            "otherIdentifiers" : [ ],
        |            "type" : "Identified"
        |          },
        |          "label" : "Plants, Medicinal",
        |          "concepts" : [
        |            {
        |              "id" : {
        |                "type" : "Unidentifiable"
        |              },
        |              "label" : "Plants, Medicinal",
        |              "type" : "Concept"
        |            }
        |          ]
        |        },
        |        {
        |          "id" : {
        |            "type" : "Unidentifiable"
        |          },
        |          "label" : "Malaya",
        |          "concepts" : [
        |            {
        |              "id" : {
        |                "type" : "Unidentifiable"
        |              },
        |              "label" : "Malaya",
        |              "type" : "Place"
        |            }
        |          ]
        |        }
        |      ],
        |      "genres" : [
        |        {
        |          "label" : "Manuscripts, Malay",
        |          "concepts" : [
        |            {
        |              "id" : {
        |                "type" : "Unidentifiable"
        |              },
        |              "label" : "Manuscripts, Malay",
        |              "type" : "Concept"
        |            }
        |          ]
        |        }
        |      ],
        |      "contributors" : [ ],
        |      "production" : [ ],
        |      "languages" : [ ],
        |      "notes" : [
        |        {
        |          "noteType" : {
        |            "id" : "contents",
        |            "label" : "Contents"
        |          },
        |          "contents" : "7A. Lists of plants, roots, woods, fibres, snakes, animals and insects. Contained in folder with a note explaining the lists are related to exhibits at the Colonial Exhibition, 1886. Malay names in Roman script; descriptions and explanations in English. -- 7B. . Lists of plants (in Roman script) collected by a Mr. Alvins in November and December, 1887. Includes date collected and location, and other notes in English. -- 7C. Lists of medicinal roots in Roman script, with their uses noted in English. -- 7D. Lists of timber 'sent to Singapore for S. S. Rumbow'. List of 30 names in Malay in Roman script. -- 7E. Names of fish, crabs, crayfish, shells. Roman script. -- 7F. Plant names in Arabic and Roman script. -- 7G. Names of fish, shellfish, etc. Roman script. "
        |        },
        |        {
        |          "noteType" : {
        |            "id" : "references-note",
        |            "label" : "References note"
        |          },
        |          "contents" : "For full details, see 'The Hervey Malay collections in the Wellcome Institute' / by R.F. Ellen, M.B. Hooker and A.C. Milner. Journal of the Malaysian Branch of the Royal Asiatic Society, vol. 54, no. 1 (1981), p. 82-92. Also included in 'Indonesian manuscripts in Great Britain: Addenda et Corrigenda' / by M.C. Ricklefs and P. Voorhoeve. Bulletin of the School of Oriental and African Studies, vol. 45, no. 2 (1982), pp. 312-315."
        |        }
        |      ],
        |      "items" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "me2ndbff",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "sierra-system-number"
        |              },
        |              "ontologyType" : "Item",
        |              "value" : "i19135853"
        |            },
        |            "otherIdentifiers" : [
        |              {
        |                "identifierType" : {
        |                  "id" : "sierra-identifier"
        |                },
        |                "ontologyType" : "Item",
        |                "value" : "1913585"
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
        |              "shelfmark" : "MS Malay 7",
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
        |        "value" : "b30172603"
        |      },
        |      "canonicalId" : "kssjs5yh",
        |      "sourceModifiedTime" : "2019-11-26T10:51:50Z",
        |      "mergeCandidates" : [ ],
        |      "internalWorkStubs" : [ ]
        |    },
        |    "redirectSources" : [ ],
        |    "type" : "Visible"
        |  }
        |""".stripMargin)

    val metsWork = fromJson[Work[Identified]](
      """
        |{
        |    "version" : 2,
        |    "data" : {
        |      "title" : "[Catalogues of Malayan plants, fish, animals and snakes]",
        |      "otherIdentifiers" : [ ],
        |      "alternativeTitles" : [ ],
        |      "subjects" : [ ],
        |      "genres" : [ ],
        |      "contributors" : [ ],
        |      "thumbnail" : {
        |        "url" : "https://iiif.wellcomecollection.org/thumbs/b30172603_wms_malay_7_b30172603_0001.JP2/full/!200,200/0/default.jpg",
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
        |              "url" : "https://iiif.wellcomecollection.org/presentation/v2/b30172603",
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
        |      "imageData" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "rgh5d5sx",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "mets-image"
        |              },
        |              "ontologyType" : "Image",
        |              "value" : "b30172603/FILE_0001_OBJECTS"
        |            },
        |            "otherIdentifiers" : [ ]
        |          },
        |          "version" : 2,
        |          "locations" : [
        |            {
        |              "url" : "https://iiif.wellcomecollection.org/image/b30172603_wms_malay_7_b30172603_0001.JP2/info.json",
        |              "locationType" : {
        |                "id" : "iiif-image"
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
        |              ]
        |            },
        |            {
        |              "url" : "https://iiif.wellcomecollection.org/presentation/v2/b30172603",
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
        |              ]
        |            }
        |          ]
        |        },
        |        {
        |          "id" : {
        |            "canonicalId" : "w7zryvu9",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "mets-image"
        |              },
        |              "ontologyType" : "Image",
        |              "value" : "b30172603/FILE_0010_OBJECTS"
        |            },
        |            "otherIdentifiers" : [ ]
        |          },
        |          "version" : 2,
        |          "locations" : [
        |            {
        |              "url" : "https://iiif.wellcomecollection.org/image/b30172603_wms_malay_7_b30172603_0010.JP2/info.json",
        |              "locationType" : {
        |                "id" : "iiif-image"
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
        |              ]
        |            },
        |            {
        |              "url" : "https://iiif.wellcomecollection.org/presentation/v2/b30172603",
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
        |              ]
        |            }
        |          ]
        |        }],
        |      "workType" : "Standard"
        |    },
        |    "state" : {
        |      "sourceIdentifier" : {
        |        "identifierType" : {
        |          "id" : "mets"
        |        },
        |        "ontologyType" : "Work",
        |        "value" : "b30172603"
        |      },
        |      "canonicalId" : "d6c24e3y",
        |      "sourceModifiedTime" : "2019-09-21T21:49:56.759Z",
        |      "mergeCandidates" : [
        |        {
        |          "id" : {
        |            "canonicalId" : "kssjs5yh",
        |            "sourceIdentifier" : {
        |              "identifierType" : {
        |                "id" : "sierra-system-number"
        |              },
        |              "ontologyType" : "Work",
        |              "value" : "b30172603"
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
        |  }
        |""".stripMargin).get

    val merger = PlatformMerger

    val result = merger
      .merge(works = Seq(teiWork, sierraWork.get, metsWork))
      .mergedWorksWithTime(now)

    val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

    visibleWorks.foreach {
      _.data.thumbnail shouldBe metsWork.data.thumbnail
    }
  }
}
