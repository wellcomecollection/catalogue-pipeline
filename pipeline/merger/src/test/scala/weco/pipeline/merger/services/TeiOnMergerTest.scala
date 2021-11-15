package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.{InternalWork, MergeCandidate, Work, WorkData}
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

    val teiWork = teiIdentifiedWork()
      .mapState {
        _.copy(
          internalWorkStubs = List(
            InternalWork.Identified(
              sourceIdentifier = createTeiSourceIdentifier,
              canonicalId = createCanonicalId,
              workData = WorkData(
                title = Some(s"tei-inner-${randomAlphanumeric(length = 10)}")
              )
            )
          )
        )
      }

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
