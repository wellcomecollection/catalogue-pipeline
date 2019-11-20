package uk.ac.wellcome.platform.api

import co.elastic.apm.attach.ElasticApmAttacher
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import uk.ac.wellcome.platform.api.works.v2.ApiV2WorksTestBase

class ApmTest
    extends FunSpec
    with ApiV2WorksTestBase
    with Matchers
    with BeforeAndAfterAll {
  override def beforeAll {
    ElasticApmAttacher.attach()
  }

  override def afterAll: Unit = {
    System.setProperty("elastic.apm.active", java.lang.Boolean.FALSE.toString)
  }

  describe("Elastic APM tracing") {
    it("goobles the fangle") {
      withApi {
        case (indexV2, routes) =>
          val works = createIdentifiedWorks(count = 3).sortBy { _.canonicalId }

          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works") {
            Status.OK ->
              s"""
            {
              ${resultList(apiPrefix, totalResults = 3)},
              "results": [
               {
                 "type": "Work",
                 "id": "${works(0).canonicalId}",
                 "title": "${works(0).data.title.get}",
                 "alternativeTitles": []
               },
               {
                 "type": "Work",
                 "id": "${works(1).canonicalId}",
                 "title": "${works(1).data.title.get}",
                 "alternativeTitles": []
               },
               {
                 "type": "Work",
                 "id": "${works(2).canonicalId}",
                 "title": "${works(2).data.title.get}",
                 "alternativeTitles": []
               }
              ]
            }
          """
          }

          Thread.sleep(60 * 1000)
      }
    }
  }
}
