package uk.ac.wellcome.platform.api.works

import akka.http.scaladsl.model.headers.LinkParams.title
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal._

class ApiWorkCollectionTest extends ApiWorksTestBase {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collectionPath = Some(CollectionPath(path = path, level = Some(level))),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  val workA = work("a", CollectionLevel.Collection)
  val workB = work("a/b", CollectionLevel.Series)
  val workC = work("a/b/c", CollectionLevel.Item)
  val workD = work("a/d", CollectionLevel.Series)
  val workE = work("a/d/e", CollectionLevel.Item)

  def workJson(work: IdentifiedWork) =
    s"""
      {
        "id": "${work.canonicalId}",
        "alternativeTitles": [],
        "type": "Work",
        "title": "${work.data.title.get}"
      }
    """

  it("includes collection tree when requested") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workC.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "a/b/c",
              "alternativeTitles": [],
              "collectionPath": {
                "path": "a/b/c",
                "level": "Item",
                "type": "CollectionPath"
              },
              "collection": {
                "path": { "path": "a", "level": "Collection", "type": "CollectionPath" },
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": { "path": "a/b", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": { "path": "a/b/c", "level": "Item", "type": "CollectionPath" },
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": { "path": "a/d", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workD)}
                  }
                ]
              }
            }
          """
        }
    }
  }

  it(
    "includes collection tree and additional multiple expanded paths when requested ") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workB.canonicalId}?include=collection&_expandPaths=a/d,a/b/c") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workB.canonicalId}",
              "title": "a/b",
              "alternativeTitles": [],
              "collectionPath": {
                "path": "a/b",
                "level": "Series",
                "type": "CollectionPath"
              },
              "collection": {
                "path": { "path": "a", "level": "Collection", "type": "CollectionPath" },
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": { "path": "a/b", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": { "path": "a/b/c", "level": "Item", "type": "CollectionPath" },
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": { "path": "a/d", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workD)},
                    "children": [
                      {
                        "path": { "path": "a/d/e", "level": "Item", "type": "CollectionPath" },
                        "work": ${workJson(workE)}
                      }
                    ]
                  }
                ]
              }
            }
          """
        }
    }
  }

  it("does not expand tree more than one level deep") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workA.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workA.canonicalId}",
              "title": "a",
              "alternativeTitles": [],
              "collectionPath": {
                "path": "a",
                "level": "Collection",
                "type": "CollectionPath"
              },
              "collection": {
                "path": { "path": "a", "level": "Collection", "type": "CollectionPath" },
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": { "path": "a/b", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workB)}
                  },
                  {
                    "path": { "path": "a/d", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workD)}
                  }
                ]
              }
            }
          """
        }
    }
  }

  it("still generates the collection tree when missing nodes") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, workA, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workC.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "a/b/c",
              "alternativeTitles": [],
              "collectionPath": {
                "path": "a/b/c",
                "level": "Item",
                "type": "CollectionPath"
              },
              "collection": {
                "path": { "path": "a", "level": "Collection", "type": "CollectionPath" },
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": { "path": "a/b", "type": "CollectionPath" },
                    "children": [
                      {
                        "path": { "path": "a/b/c", "level": "Item", "type": "CollectionPath" },
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": { "path": "a/d", "level": "Series", "type": "CollectionPath" },
                    "work": ${workJson(workD)}
                  }
                ]
              }
            }
          """
        }
    }
  }
}
