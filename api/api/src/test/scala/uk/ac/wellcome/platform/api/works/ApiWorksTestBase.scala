package uk.ac.wellcome.platform.api.works
import com.sksamuel.elastic4s.{ElasticDsl, Index, Indexable}
import com.sksamuel.elastic4s.ElasticDsl._
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.{GenreGenerators, SubjectGenerators, WorksGenerators}
import uk.ac.wellcome.models.work.internal.{IdentifiedWork, Language, WorkType}
import uk.ac.wellcome.platform.api.fixtures.ApiFixture
import uk.ac.wellcome.fixtures._

trait ApiWorksTestBase
    extends ApiFixture
    with WorksGenerators
    with GenreGenerators
    with SubjectGenerators {

  implicit object IdentifiedWorkIndexable extends Indexable[IdentifiedWork] {
    override def json(t: IdentifiedWork): String =
      toJson(t).get
  }

  def getApiPrefix(
    apiVersion: ApiVersions.Value = ApiVersions.default): String =
    apiName + "/" + apiVersion

  val apiScheme = "https"
  val apiHost = "api-testing.local"
  val apiName = "catalogue"

  def contextUrl(apiPrefix: String): String =
    s"$apiScheme://$apiHost/$apiPrefix/context.json"

  def emptyJsonResult(apiPrefix: String): String = s"""
    |{
    |  ${resultList(apiPrefix, totalPages = 0, totalResults = 0)},
    |  "results": []
    |}""".stripMargin

  def badRequest(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 400,
      "label": "Bad Request",
      "description": "$description"
    }"""

  def goneRequest(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 410,
      "label": "Gone",
      "description": "$description"
    }"""

  def resultList(apiPrefix: String,
                 pageSize: Int = 10,
                 totalPages: Int = 1,
                 totalResults: Int) =
    s"""
      "@context": "${contextUrl(apiPrefix)}",
      "type": "ResultList",
      "pageSize": $pageSize,
      "totalPages": $totalPages,
      "totalResults": $totalResults
    """

  def singleWorkResult(apiPrefix: String): String =
    s"""
        "@context": "${contextUrl(apiPrefix)}",
        "type": "Work"
     """.stripMargin

  def notFound(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 404,
      "label": "Not Found",
      "description": "$description"
    }"""

  def deleted(apiPrefix: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 410,
      "label": "Gone",
      "description": "This work has been deleted"
    }"""

  def workResponse(work: IdentifiedWork): String =
    s"""
      | {
      |   "type": "Work",
      |   "id": "${work.canonicalId}",
      |   "title": "${work.data.title.get}",
      |   ${work.data.workType.map(workTypeResponse).getOrElse("")}
      |   ${work.data.language.map(languageResponse).getOrElse("")}
      |   "alternativeTitles": []
      | }
    """.stripMargin

  def worksListResponse(apiPrefix: String, works: Seq[IdentifiedWork]): String =
    s"""
       |{
       |  ${resultList(apiPrefix, totalResults = works.size)},
       |  "results": [
       |    ${works.map { workResponse }.mkString(",")}
       |  ]
       |}
      """.stripMargin

  def workTypeResponse(workType: WorkType): String =
    s"""
      | "workType": {
      |   "id": "${workType.id}",
      |   "label": "${workType.label}",
      |   "type": "WorkType"
      | },
    """.stripMargin

  def languageResponse(language: Language): String =
    s"""
      | "language": {
      |   "id": "${language.id}",
      |   "label": "${language.label}",
      |   "type": "Language"
      | },
    """.stripMargin

  def withEmptyIndex[R]: Fixture[Index, R] =
    fixture[Index, R](
      create = {
        val index = Index(randomAlphanumeric(length = 10))
        elasticClient
          .execute {
            ElasticDsl.createIndex(index.name)
          }
        eventuallyIndexExists(index)
        index
      },
      destroy = eventuallyDeleteIndex
    )
}
