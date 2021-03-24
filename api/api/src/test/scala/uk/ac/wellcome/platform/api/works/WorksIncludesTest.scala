package uk.ac.wellcome.platform.api.works

import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.models.work.generators.{
  ProductionEventGenerators,
  SubjectGenerators
}
import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work._

class WorksIncludesTest
    extends ApiWorksTestBase
    with ProductionEventGenerators
    with SubjectGenerators
    with ImageGenerators {

  val canonicalId1 = CanonicalId("00000000")
  val canonicalId2 = CanonicalId("11111111")

  describe("identifiers includes") {
    it(
      "includes a list of identifiers on a list endpoint if we pass ?include=identifiers") {
      withWorksApi {
        case (worksIndex, routes) =>
          val otherIdentifier1 = createSourceIdentifier
          val otherIdentifier2 = createSourceIdentifier
          val work1 = indexedWork(canonicalId = canonicalId1)
            .otherIdentifiers(List(otherIdentifier1))
          val work2 = indexedWork(canonicalId = canonicalId2)
            .otherIdentifiers(List(otherIdentifier2))

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=identifiers") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "identifiers": [
                     ${identifier(work1.sourceIdentifier)},
                     ${identifier(otherIdentifier1)}
                   ]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "identifiers": [
                     ${identifier(work2.sourceIdentifier)},
                     ${identifier(otherIdentifier2)}
                   ]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of identifiers on a single work endpoint if we pass ?include=identifiers") {
      withWorksApi {
        case (worksIndex, routes) =>
          val otherIdentifier = createSourceIdentifier
          val work = indexedWork().otherIdentifiers(List(otherIdentifier))
          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=identifiers") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "identifiers": [
                  ${identifier(work.sourceIdentifier)},
                  ${identifier(otherIdentifier)}
                ]
              }
            """
          }
      }
    }
  }

  it("renders the items if the items include is present") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = indexedWork()
          .items(
            List(
              createIdentifiedItemWith(title = Some("item title")),
              createUnidentifiableItem
            ))

        insertIntoElasticsearch(worksIndex, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.state.canonicalId}?include=items") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.state.canonicalId}",
              "title": "${work.data.title.get}",
              "alternativeTitles": [],
              "availabilities": [${availabilities(work.state.availabilities)}],
              "items": [ ${items(work.data.items)} ]
            }
          """
        }
    }
  }

  describe("subject includes") {
    it(
      "includes a list of subjects on a list endpoint if we pass ?include=subjects") {
      withWorksApi {
        case (worksIndex, routes) =>
          val subjects1 = List(createSubject)
          val subjects2 = List(createSubject)
          val work1 =
            indexedWork(canonicalId = canonicalId1).subjects(subjects1)
          val work2 =
            indexedWork(canonicalId = canonicalId2).subjects(subjects2)

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=subjects") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "subjects": [ ${subjects(subjects1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "subjects": [ ${subjects(subjects2)}]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of subjects on a single work endpoint if we pass ?include=subjects") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork().subjects(List(createSubject))

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=subjects") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "subjects": [ ${subjects(work.data.subjects)}]
              }
            """
          }
      }
    }
  }

  describe("genre includes") {
    it(
      "includes a list of genres on a list endpoint if we pass ?include=genres") {
      withWorksApi {
        case (worksIndex, routes) =>
          val genres1 = List(Genre("ornithology", List(Concept("ornithology"))))
          val genres2 = List(Genre("flying cars", List(Concept("flying cars"))))
          val work1 = indexedWork(canonicalId = canonicalId1).genres(genres1)
          val work2 = indexedWork(canonicalId = canonicalId2).genres(genres2)

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=genres") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "genres": [ ${genres(genres1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "genres": [ ${genres(genres2)}]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of genres on a single work endpoint if we pass ?include=genres") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork().genres(
            List(Genre("ornithology", List(Concept("ornithology")))))

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=genres") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "genres": [ ${genres(work.data.genres)}]
              }
            """
          }
      }
    }
  }

  describe("contributor includes") {
    it(
      "includes a list of contributors on a list endpoint if we pass ?include=contributors") {
      withWorksApi {
        case (worksIndex, routes) =>
          val contributors1 =
            List(Contributor(Person("Ginger Rogers"), roles = Nil))
          val contributors2 =
            List(Contributor(Person("Fred Astair"), roles = Nil))
          val work1 =
            indexedWork(canonicalId = canonicalId1).contributors(contributors1)
          val work2 =
            indexedWork(canonicalId = canonicalId2).contributors(contributors2)

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works/?include=contributors") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "contributors": [ ${contributors(contributors1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "contributors": [ ${contributors(contributors2)}]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of contributors on a single work endpoint if we pass ?include=contributors") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork()
            .contributors(
              List(Contributor(Person("Ginger Rogers"), roles = Nil)))

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=contributors") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "contributors": [ ${contributors(work.data.contributors)}]
              }
            """
          }
      }
    }
  }

  describe("production includes") {
    it(
      "includes a list of production events on a list endpoint if we pass ?include=production") {
      withWorksApi {
        case (worksIndex, routes) =>
          val productionEvents1 = createProductionEventList()
          val productionEvents2 = createProductionEventList()
          val work1 =
            indexedWork(canonicalId = canonicalId1)
              .production(productionEvents1)
          val work2 =
            indexedWork(canonicalId = canonicalId2)
              .production(productionEvents2)

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=production") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "production": [ ${production(productionEvents1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "production": [ ${production(productionEvents2)}]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of production on a single work endpoint if we pass ?include=production") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork().production(createProductionEventList())

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=production") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "production": [ ${production(work.data.production)}]
              }
            """
          }
      }
    }
  }

  describe("languages includes") {
    it("includes languages on a list endpoint if we pass ?include=languages") {
      withWorksApi {
        case (worksIndex, routes) =>
          val english = Language(label = "English", id = "eng")
          val turkish = Language(label = "Turkish", id = "tur")
          val swedish = Language(label = "Swedish", id = "swe")

          val work1 =
            indexedWork(canonicalId = canonicalId1)
              .languages(List(english, turkish))
          val work2 =
            indexedWork(canonicalId = canonicalId2).languages(List(swedish))

          insertIntoElasticsearch(worksIndex, work1, work2)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=languages") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work1.state.availabilities)}],
                   "languages": [ ${languages(work1.data.languages)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
                   "availabilities": [${availabilities(
              work2.state.availabilities)}],
                   "languages": [ ${languages(work2.data.languages)}]
                 }
                ]
              }
            """
          }
      }
    }

    it("includes languages on a work endpoint if we pass ?include=languages") {
      withWorksApi {
        case (worksIndex, routes) =>
          val english = Language(label = "English", id = "eng")
          val turkish = Language(label = "Turkish", id = "tur")
          val swedish = Language(label = "Swedish", id = "swe")

          val work = indexedWork().languages(List(english, turkish, swedish))

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=languages") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "languages": [ ${languages(work.data.languages)}]
              }
            """
          }
      }
    }
  }

  describe("notes includes") {
    it("includes notes on the list endpoint if we pass ?include=notes") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work1 = indexedWork(canonicalId = canonicalId1)
            .notes(List(GeneralNote("GN1"), FundingInformation("FI1")))
          val work2 = indexedWork(canonicalId = canonicalId2)
            .notes(List(GeneralNote("GN2.1"), GeneralNote("GN2.2")))

          insertIntoElasticsearch(worksIndex, work1, work2)
          assertJsonResponse(routes, s"/$apiPrefix/works?include=notes") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                   {
                     "type": "Work",
                     "id": "${work1.state.canonicalId}",
                     "title": "${work1.data.title.get}",
                     "alternativeTitles": [],
                     "availabilities": [${availabilities(
              work1.state.availabilities)}],
                     "notes": [
                       {
                         "noteType": {
                           "id": "general-note",
                           "label": "Notes",
                           "type": "NoteType"
                         },
                         "contents": ["GN1"],
                         "type": "Note"
                       },
                       {
                         "noteType": {
                           "id": "funding-info",
                           "label": "Funding information",
                           "type": "NoteType"
                         },
                         "contents": ["FI1"],
                         "type": "Note"
                       }
                     ]
                   },
                   {
                     "type": "Work",
                     "id": "${work2.state.canonicalId}",
                     "title": "${work2.data.title.get}",
                     "alternativeTitles": [],
                     "availabilities": [${availabilities(
              work2.state.availabilities)}],
                     "notes": [
                       {
                         "noteType": {
                           "id": "general-note",
                           "label": "Notes",
                           "type": "NoteType"
                         },
                         "contents": ["GN2.1", "GN2.2"],
                         "type": "Note"
                       }
                     ]
                  }
                ]
              }
            """
          }
      }
    }

    it("includes notes on the single work endpoint if we pass ?include=notes") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work =
            indexedWork().notes(List(GeneralNote("A"), GeneralNote("B")))
          insertIntoElasticsearch(worksIndex, work)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=notes") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "notes": [
                   {
                     "noteType": {
                       "id": "general-note",
                       "label": "Notes",
                       "type": "NoteType"
                     },
                     "contents": ["A", "B"],
                     "type": "Note"
                   }
                ]
              }
            """
          }
      }
    }
  }

  describe("image includes") {
    it(
      "includes a list of images on the list endpoint if we pass ?include=images") {
      withWorksApi {
        case (worksIndex, routes) =>
          val works = List(
            indexedWork()
              .imageData(
                (1 to 3)
                  .map(_ => createImageData.toIdentified)
                  .toList),
            indexedWork()
              .imageData(
                (1 to 3)
                  .map(_ => createImageData.toIdentified)
                  .toList)
          ).sortBy { _.state.canonicalId }

          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=images") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = works.size)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${works.head.state.canonicalId}",
                    "title": "${works.head.data.title.get}",
                    "alternativeTitles": [],
                    "availabilities": [${availabilities(
              works.head.state.availabilities)}],
                    "images": [${workImageIncludes(works.head.data.imageData)}]
                  },
                  {
                    "type": "Work",
                    "id": "${works(1).state.canonicalId}",
                    "title": "${works(1).data.title.get}",
                    "alternativeTitles": [],
                    "availabilities": [${availabilities(
              works(1).state.availabilities)}],
                    "images": [${workImageIncludes(works(1).data.imageData)}]
                  }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of images on a single work endpoint if we pass ?include=images") {
      withWorksApi {
        case (worksIndex, routes) =>
          val images =
            (1 to 3).map(_ => createImageData.toIdentified).toList
          val work = indexedWork().imageData(images)

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=images") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "images": [${workImageIncludes(images)}]
              }
            """
          }
      }
    }
  }

  describe("relation includes") {
    def work(path: String,
             workType: WorkType): Work.Visible[WorkState.Indexed] =
      indexedWork(sourceIdentifier = createSourceIdentifierWith(value = path))
        .collectionPath(CollectionPath(path = path))
        .title(path)
        .workType(workType)

    val work0 = work("0", WorkType.Collection)
    val workA = work("0/a", WorkType.Section)
    val workB = work("0/a/b", WorkType.Standard)
    val workD = work("0/a/d", WorkType.Standard)
    val workE = work("0/a/c/e", WorkType.Standard)

    val workC =
      indexedWork(
        sourceIdentifier = createSourceIdentifierWith(value = "0/a/c"),
        relations = Relations(
          ancestors = List(
            Relation(work0, 0, 1, 5),
            Relation(workA, 1, 3, 4),
          ),
          children = List(Relation(workE, 3, 0, 0)),
          siblingsPreceding = List(Relation(workB, 2, 0, 0)),
          siblingsSucceeding = List(Relation(workD, 2, 0, 0)),
        )
      ).collectionPath(CollectionPath(path = "0/a/c"))
        .title("0/a/c")
        .workType(WorkType.Series)

    def storeWorks(index: Index) =
      insertIntoElasticsearch(index, work0, workA, workB, workC, workD, workE)

    it("includes parts") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=parts") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "availabilities": [${availabilities(workC.state.availabilities)}],
              "parts": [{
                "id": "${workE.state.canonicalId}",
                "title": "0/a/c/e",
                "totalParts": 0,
                "totalDescendentParts": 0,
                "type": "Work"
              }]
            }
          """
          }
      }
    }

    it("includes partOf") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=partOf") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "availabilities": [${availabilities(workC.state.availabilities)}],
              "partOf": [
                {
                  "id": "${workA.state.canonicalId}",
                  "title": "0/a",
                  "totalParts": 3,
                  "totalDescendentParts": 4,
                  "type": "Section",
                  "partOf": [{
                    "id": "${work0.state.canonicalId}",
                    "title": "0",
                    "totalParts": 1,
                    "totalDescendentParts": 5,
                    "type": "Collection",
                    "partOf": []
                  }
                ]
              }]
            }
          """
          }
      }
    }

    it("includes precededBy") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=precededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "availabilities": [${availabilities(workC.state.availabilities)}],
              "precededBy": [{
                "id": "${workB.state.canonicalId}",
                "title": "0/a/b",
                "totalParts": 0,
                "totalDescendentParts": 0,
                "type": "Work"
              }]
            }
          """
          }
      }
    }

    it("includes succeededBy") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=succeededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "availabilities": [${availabilities(workC.state.availabilities)}],
              "succeededBy": [{
                "id": "${workD.state.canonicalId}",
                "title": "0/a/d",
                "totalParts": 0,
                "totalDescendentParts": 0,
                "type": "Work"
              }]
            }
          """
          }
      }
    }
  }

  describe("holdings includes") {
    def createHoldings(count: Int): List[Holdings] =
      (1 to count).map { _ =>
        Holdings(
          note = chooseFrom(None, Some(randomAlphanumeric())),
          enumeration =
            collectionOf(min = 0, max = 10) { randomAlphanumeric() }.toList,
          location = chooseFrom(None, Some(createPhysicalLocation))
        )
      }.toList

    it("on the list endpoint") {
      withWorksApi {
        case (worksIndex, routes) =>
          val works = List(
            indexedWork().holdings(createHoldings(3)),
            indexedWork().holdings(createHoldings(4))
          ).sortBy { _.state.canonicalId }

          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=holdings") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = works.size)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${works.head.state.canonicalId}",
                    "title": "${works.head.data.title.get}",
                    "alternativeTitles": [],
                    "availabilities": [${availabilities(
              works.head.state.availabilities)}],
                    "holdings": [${listOfHoldings(works.head.data.holdings)}]
                  },
                  {
                    "type": "Work",
                    "id": "${works(1).state.canonicalId}",
                    "title": "${works(1).data.title.get}",
                    "alternativeTitles": [],
                    "availabilities": [${availabilities(
              works(1).state.availabilities)}],
                    "holdings": [${listOfHoldings(works(1).data.holdings)}]
                  }
                ]
              }
            """
          }
      }
    }

    it("on a single work endpoint") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork().holdings(createHoldings(3))

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}?include=holdings") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.state.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "availabilities": [${availabilities(work.state.availabilities)}],
                "holdings": [${listOfHoldings(work.data.holdings)}]
              }
            """
          }
      }
    }
  }
}
