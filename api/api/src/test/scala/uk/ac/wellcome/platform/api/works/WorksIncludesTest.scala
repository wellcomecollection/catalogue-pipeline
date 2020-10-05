package uk.ac.wellcome.platform.api.works

import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.generators.{
  ImageGenerators,
  ProductionEventGenerators,
  SubjectGenerators
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._

class WorksIncludesTest
    extends ApiWorksTestBase
    with ProductionEventGenerators
    with SubjectGenerators
    with ImageGenerators {

  describe("identifiers includes") {
    it(
      "includes a list of identifiers on a list endpoint if we pass ?include=identifiers") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val otherIdentifier0 = createSourceIdentifier
          val otherIdentifier1 = createSourceIdentifier
          val work0 = identifiedWork(canonicalId = "0")
            .otherIdentifiers(List(otherIdentifier0))
          val work1 = identifiedWork(canonicalId = "1")
            .otherIdentifiers(List(otherIdentifier1))

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=identifiers") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.state.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "identifiers": [
                     ${identifier(work0.sourceIdentifier)},
                     ${identifier(otherIdentifier0)}
                   ]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "identifiers": [
                     ${identifier(work1.sourceIdentifier)},
                     ${identifier(otherIdentifier1)}
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val otherIdentifier = createSourceIdentifier
          val work = identifiedWork().otherIdentifiers(List(otherIdentifier))
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
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work = identifiedWork()
          .items(
            List(
              createIdentifiedItemWith(title = Some("item title")),
              createUnidentifiableItemWith()
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
              "items": [ ${items(work.data.items)} ]
            }
          """
        }
    }
  }

  describe("subject includes") {
    it(
      "includes a list of subjects on a list endpoint if we pass ?include=subjects") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val subjects0 = List(createSubject)
          val subjects1 = List(createSubject)
          val work0 = identifiedWork(canonicalId = "0").subjects(subjects0)
          val work1 = identifiedWork(canonicalId = "1").subjects(subjects1)

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=subjects") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.state.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "subjects": [ ${subjects(subjects0)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.state.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "subjects": [ ${subjects(subjects1)}]
                 }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of subjects on a single work endpoint if we pass ?include=subjects") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work = identifiedWork().subjects(List(createSubject))

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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val genres1 = List(Genre("ornithology", List(Concept("ornithology"))))
          val genres2 = List(Genre("flying cars", List(Concept("flying cars"))))
          val work1 = identifiedWork(canonicalId = "1").genres(genres1)
          val work2 = identifiedWork(canonicalId = "2").genres(genres2)

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
                   "genres": [ ${genres(genres1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work = identifiedWork().genres(
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val contributors1 =
            List(Contributor(Person("Ginger Rogers"), roles = Nil))
          val contributors2 =
            List(Contributor(Person("Fred Astair"), roles = Nil))
          val work1 =
            identifiedWork(canonicalId = "1").contributors(contributors1)
          val work2 =
            identifiedWork(canonicalId = "2").contributors(contributors2)

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
                   "contributors": [ ${contributors(contributors1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work = identifiedWork()
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val productionEvents1 = createProductionEventList()
          val productionEvents2 = createProductionEventList()
          val work1 =
            identifiedWork(canonicalId = "1").production(productionEvents1)
          val work2 =
            identifiedWork(canonicalId = "2").production(productionEvents2)

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
                   "production": [ ${production(productionEvents1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work2.state.canonicalId}",
                   "title": "${work2.data.title.get}",
                   "alternativeTitles": [],
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work = identifiedWork().production(createProductionEventList())

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
                "production": [ ${production(work.data.production)}]
              }
            """
          }
      }
    }
  }

  describe("notes includes") {
    it("includes notes on the list endpoint if we pass ?include=notes") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work1 = identifiedWork(canonicalId = "1")
            .notes(List(GeneralNote("GN1"), FundingInformation("FI1")))
          val work2 = identifiedWork(canonicalId = "2")
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work =
            identifiedWork().notes(List(GeneralNote("A"), GeneralNote("B")))
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val works = List(
            identifiedWork()
              .images(
                (1 to 3).map(_ => createUnmergedImage.toIdentified).toList),
            identifiedWork()
              .images(
                (1 to 3).map(_ => createUnmergedImage.toIdentified).toList)
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
                    "images": [${workImageIncludes(works.head.data.images)}]
                  },
                  {
                    "type": "Work",
                    "id": "${works(1).state.canonicalId}",
                    "title": "${works(1).data.title.get}",
                    "alternativeTitles": [],
                    "images": [${workImageIncludes(works(1).data.images)}]
                  }
                ]
              }
            """
          }
      }
    }

    it(
      "includes a list of images on a single work endpoint if we pass ?include=images") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val images =
            (1 to 3).map(_ => createUnmergedImage.toIdentified).toList
          val work = identifiedWork().images(images)

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
                "images": [${workImageIncludes(images)}]
              }
            """
          }
      }
    }
  }

  describe("relation includes") {
    def work(path: String,
             workType: WorkType): Work.Visible[WorkState.Identified] =
      identifiedWork(
        sourceIdentifier = createSourceIdentifierWith(value = path))
        .collectionPath(CollectionPath(path = path))
        .title(path)
        .workType(workType)

    val work0 = work("0", WorkType.Collection)
    val workA = work("0/a", WorkType.Section)
    val workB = work("0/a/b", WorkType.Standard)
    val workC = work("0/a/c", WorkType.Series)
    val workD = work("0/a/d", WorkType.Standard)
    val workE = work("0/a/c/e", WorkType.Standard)

    def storeWorks(index: Index) =
      insertIntoElasticsearch(index, work0, workA, workB, workC, workD, workE)

    it("includes parts") {
      withApi {
        case (ElasticConfig(index, _), routes) =>
          storeWorks(index)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=parts") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "parts": [{
                "id": "${workE.state.canonicalId}",
                "title": "0/a/c/e",
                "alternativeTitles": [],
                "type": "Work"
              }]
            }
          """
          }
      }
    }

    it("includes partOf") {
      withApi {
        case (ElasticConfig(index, _), routes) =>
          storeWorks(index)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=partOf") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "partOf": [
                {
                  "id": "${workA.state.canonicalId}",
                  "title": "0/a",
                  "alternativeTitles": [],
                  "type": "Work",
                  "partOf": [{
                    "id": "${work0.state.canonicalId}",
                    "title": "0",
                    "alternativeTitles": [],
                    "type": "Work",
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
      withApi {
        case (ElasticConfig(index, _), routes) =>
          storeWorks(index)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=precededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "precededBy": [{
                "id": "${workB.state.canonicalId}",
                "title": "0/a/b",
                "alternativeTitles": [],
                "type": "Work"
              }]
            }
          """
          }
      }
    }

    it("includes succeededBy") {
      withApi {
        case (ElasticConfig(index, _), routes) =>
          storeWorks(index)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.state.canonicalId}?include=succeededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix, "Series")},
              "id": "${workC.state.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "succeededBy": [{
                "id": "${workD.state.canonicalId}",
                "title": "0/a/d",
                "alternativeTitles": [],
                "type": "Work"
              }]
            }
          """
          }
      }
    }
  }
}
