package uk.ac.wellcome.platform.api.works

import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.generators.{
  ImageGenerators,
  ProductionEventGenerators,
  SubjectGenerators
}
import uk.ac.wellcome.models.work.internal._

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
          val identifier0 = createSourceIdentifier
          val identifier1 = createSourceIdentifier
          val work0 = createIdentifiedWorkWith(
            canonicalId = "1",
            otherIdentifiers = List(identifier0))
          val work1 = createIdentifiedWorkWith(
            canonicalId = "2",
            otherIdentifiers = List(identifier1))

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=identifiers") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "identifiers": [
                     ${identifier(work0.sourceIdentifier)},
                     ${identifier(identifier0)}
                   ]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
                   "identifiers": [
                     ${identifier(work1.sourceIdentifier)},
                     ${identifier(identifier1)}
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
          val work = createIdentifiedWorkWith(
            otherIdentifiers = List(otherIdentifier)
          )
          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=identifiers") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
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
        val work = createIdentifiedWorkWith(
          items = List(
            createIdentifiedItemWith(title = Some("item title")),
            createUnidentifiableItemWith()
          )
        )

        insertIntoElasticsearch(worksIndex, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=items") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
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
          val subjects1 = List(createSubject)
          val subjects2 = List(createSubject)
          val work0 =
            createIdentifiedWorkWith(canonicalId = "1", subjects = subjects1)
          val work1 =
            createIdentifiedWorkWith(canonicalId = "2", subjects = subjects2)

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=subjects") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "subjects": [ ${subjects(subjects1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.canonicalId}",
                   "title": "${work1.data.title.get}",
                   "alternativeTitles": [],
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
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val subject = List(createSubject)
          val work = createIdentifiedWorkWith(subjects = subject)

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=subjects") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "subjects": [ ${subjects(subject)}]
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
          val work0 =
            createIdentifiedWorkWith(canonicalId = "1", genres = genres1)
          val work1 =
            createIdentifiedWorkWith(canonicalId = "2", genres = genres2)

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=genres") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "genres": [ ${genres(genres1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.canonicalId}",
                   "title": "${work1.data.title.get}",
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
          val genre = List(Genre("ornithology", List(Concept("ornithology"))))
          val work = createIdentifiedWorkWith(genres = genre)

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=genres") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "genres": [ ${genres(genre)}]
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
          val work0 = createIdentifiedWorkWith(
            canonicalId = "1",
            contributors = contributors1)
          val work1 = createIdentifiedWorkWith(
            canonicalId = "2",
            contributors = contributors2)

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works/?include=contributors") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "contributors": [ ${contributors(contributors1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.canonicalId}",
                   "title": "${work1.data.title.get}",
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
          val contributor =
            List(Contributor(Person("Ginger Rogers"), roles = Nil))
          val work = createIdentifiedWorkWith(contributors = contributor)

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=contributors") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "contributors": [ ${contributors(contributor)}]
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
          val productionEvents1 = createProductionEventList(count = 1)
          val productionEvents2 = createProductionEventList(count = 2)
          val work0 = createIdentifiedWorkWith(
            canonicalId = "1",
            production = productionEvents1)
          val work1 = createIdentifiedWorkWith(
            canonicalId = "2",
            production = productionEvents2)

          insertIntoElasticsearch(worksIndex, work0, work1)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=production") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                 {
                   "type": "Work",
                   "id": "${work0.canonicalId}",
                   "title": "${work0.data.title.get}",
                   "alternativeTitles": [],
                   "production": [ ${production(productionEvents1)}]
                 },
                 {
                   "type": "Work",
                   "id": "${work1.canonicalId}",
                   "title": "${work1.data.title.get}",
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
          val productionEventList = createProductionEventList()
          val work = createIdentifiedWorkWith(
            production = productionEventList
          )

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=production") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
                "title": "${work.data.title.get}",
                "alternativeTitles": [],
                "production": [ ${production(productionEventList)}]
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
          val works = List(
            createIdentifiedWorkWith(
              canonicalId = "A",
              notes = List(GeneralNote("A"), FundingInformation("B"))),
            createIdentifiedWorkWith(
              canonicalId = "B",
              notes = List(GeneralNote("C"), GeneralNote("D"))),
          )
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?include=notes") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                   {
                     "type": "Work",
                     "id": "${works(0).canonicalId}",
                     "title": "${works(0).data.title.get}",
                     "alternativeTitles": [],
                     "notes": [
                       {
                         "noteType": {
                           "id": "general-note",
                           "label": "Notes",
                           "type": "NoteType"
                         },
                         "contents": ["A"],
                         "type": "Note"
                       },
                       {
                         "noteType": {
                           "id": "funding-info",
                           "label": "Funding information",
                           "type": "NoteType"
                         },
                         "contents": ["B"],
                         "type": "Note"
                       }
                     ]
                   },
                   {
                     "type": "Work",
                     "id": "${works(1).canonicalId}",
                     "title": "${works(1).data.title.get}",
                     "alternativeTitles": [],
                     "notes": [
                       {
                         "noteType": {
                           "id": "general-note",
                           "label": "Notes",
                           "type": "NoteType"
                         },
                         "contents": ["C", "D"],
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
          val work = createIdentifiedWorkWith(
            notes = List(GeneralNote("A"), GeneralNote("B")))
          insertIntoElasticsearch(worksIndex, work)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=notes") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
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

  describe("collection includes") {
    it(
      "includes collection on the list endpoint if we pass ?include=collection") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val works = List(
            createIdentifiedWorkWith(
              canonicalId = "1",
              collectionPath = Some(
                CollectionPath(
                  "PP/MI",
                  Some(CollectionLevel.Item),
                  Some("PP/MI")))),
            createIdentifiedWorkWith(
              canonicalId = "2",
              collectionPath = Some(
                CollectionPath(
                  "CRGH",
                  Some(CollectionLevel.Collection),
                  Some("CRGH")))),
          )
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?include=collection") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                   {
                     "type": "Work",
                     "id": "${works.head.canonicalId}",
                     "title": "${works.head.data.title.get}",
                     "referenceNumber": "PP/MI",
                     "alternativeTitles": [],
                     "collectionPath": {
                        "label": "PP/MI",
                        "path": "PP/MI",
                        "level": "Item",
                        "type" : "CollectionPath"
                     }
                   },
                   {
                     "type": "Work",
                     "id": "${works(1).canonicalId}",
                     "title": "${works(1).data.title.get}",
                     "referenceNumber": "CRGH",
                     "alternativeTitles": [],
                     "collectionPath": {
                        "label": "CRGH",
                        "path": "CRGH",
                        "level": "Collection",
                        "type" : "CollectionPath"
                     }
                  }
                ]
              }
            """
          }
      }
    }

    it(
      "includes collection on the single work endpoint if we pass ?include=collection") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val work = createIdentifiedWorkWith(collectionPath = Some(
            CollectionPath("PP/MI", Some(CollectionLevel.Item), Some("PP/MI"))))
          insertIntoElasticsearch(worksIndex, work)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=collection") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
                "title": "${work.data.title.get}",
                "referenceNumber": "PP/MI",
                "alternativeTitles": [],
                "collectionPath": {
                  "label": "PP/MI",
                  "path": "PP/MI",
                  "level": "Item",
                  "type" : "CollectionPath"
                },
                "collection" : {
                  "path" : { "path" : "PP", "type" : "CollectionPath" },
                  "children" : [
                    {
                      "path" : {
                        "label" : "PP/MI",
                        "level" : "Item",
                        "path" : "PP/MI",
                        "type" : "CollectionPath"
                      },
                      "work" : {
                        "id": "${work.canonicalId}",
                        "title": "${work.data.title.get}",
                        "referenceNumber": "PP/MI",
                        "alternativeTitles" : [],
                        "type" : "Work"
                      },
                      "children" : []
                    }
                  ]
                }
              }
            """
          }
      }
    }
  }

  it(
    "doesn't include collection on the single work endpoint if we don't pass ?include=collection") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work = createIdentifiedWorkWith(
          collectionPath = Some(
            CollectionPath("PP/MI", Some(CollectionLevel.Item), Some("PP/MI"))))
        insertIntoElasticsearch(worksIndex, work)
        assertJsonResponse(routes, s"/$apiPrefix/works/${work.canonicalId}") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.data.title.get}",
              "referenceNumber": "PP/MI",
              "alternativeTitles": []
            }
          """
        }
    }
  }

  describe("image includes") {
    it(
      "includes a list of images on the list endpoint if we pass ?include=images") {
      withApi {
        case (ElasticConfig(worksIndex, _), routes) =>
          val works = List(
            createIdentifiedWorkWith(
              canonicalId = "A",
              images =
                (1 to 3).map(_ => createUnmergedImage.toIdentified).toList
            ),
            createIdentifiedWorkWith(
              canonicalId = "B",
              images =
                (1 to 3).map(_ => createUnmergedImage.toIdentified).toList
            )
          )

          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?include=images") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${works.head.canonicalId}",
                    "title": "${works.head.data.title.get}",
                    "alternativeTitles": [],
                    "images": [${workImageIncludes(works.head.data.images)}]
                  },
                  {
                    "type": "Work",
                    "id": "${works(1).canonicalId}",
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
          val work = createIdentifiedWorkWith(images = images)

          insertIntoElasticsearch(worksIndex, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.canonicalId}?include=images") {
            Status.OK -> s"""
              {
                ${singleWorkResult(apiPrefix)},
                "id": "${work.canonicalId}",
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
    def work(path: String) =
      createIdentifiedWorkWith(
        collectionPath = Some(CollectionPath(path = path)),
        title = Some(path),
        sourceIdentifier = createSourceIdentifierWith(value = path)
      )

    val work0 = work("0")
    val workA = work("0/a")
    val workB = work("0/a/b")
    val workC = work("0/a/c")
    val workD = work("0/a/d")
    val workE = work("0/a/c/e")

    def storeWorks(index: Index) =
      insertIntoElasticsearch(index, work0, workA, workB, workC, workD, workE)

    it("includes parts") {
      withApi {
        case (ElasticConfig(index, _), routes) =>
          storeWorks(index)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${workC.canonicalId}?include=parts") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "parts": [{
                "id": "${workE.canonicalId}",
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
            s"/$apiPrefix/works/${workC.canonicalId}?include=partOf") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "partOf": [{
                "id": "${workA.canonicalId}",
                "title": "0/a",
                "alternativeTitles": [],
                "type": "Work",
                "partOf": [{
                  "id": "${work0.canonicalId}",
                  "title": "0",
                  "alternativeTitles": [],
                  "type": "Work",
                  "partOf": []
                }]
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
            s"/$apiPrefix/works/${workC.canonicalId}?include=precededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "precededBy": [{
                "id": "${workB.canonicalId}",
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
            s"/$apiPrefix/works/${workC.canonicalId}?include=succeededBy") {
            Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "0/a/c",
              "alternativeTitles": [],
              "succeededBy": [{
                "id": "${workD.canonicalId}",
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
