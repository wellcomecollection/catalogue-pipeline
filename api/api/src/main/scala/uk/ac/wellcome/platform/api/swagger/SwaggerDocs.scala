package uk.ac.wellcome.platform.api.swagger

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import io.swagger.v3.core.util.Json
import io.swagger.v3.jaxrs2.Reader
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import javax.ws.rs.{GET, Path}
import grizzled.slf4j.Logging

import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.models.v2._
import uk.ac.wellcome.platform.api.MultipleWorksResponse

class SwaggerDocs(apiConfig: ApiConfig) extends Logging {

  val version = ApiVersions.v2

  val url =
    s"${apiConfig.scheme}://${apiConfig.host}/${apiConfig.pathPrefix}/$version/"

  val server = (new Server).url(url)

  val info = new Info()
    .description("Search our collections")
    .version(version.toString)
    .title("Catalogue")

  val apiClasses: Set[Class[_]] =
    Set(classOf[SingleWorkSwagger], classOf[MultipleWorksSwagger])

  val openAPI = new OpenAPI()
    .info(info)
    .servers(ListBuffer(server).asJava)

  val config = (new SwaggerConfiguration)
    .openAPI(openAPI)

  val json: String =
    Json.pretty(new Reader(config).read(apiClasses.asJava))
}

@Path("/works/{id}")
trait SingleWorkSwagger {

  @GET
  @Tag(name = "Works")
  @Operation(
    summary = "/works/{id}",
    description = "Returns a single work",
    tags = Array("Works"),
    parameters = Array(
      new Parameter(
        name = "id",
        in = ParameterIn.PATH,
        description = "The work to return",
        required = true
      ),
      new Parameter(
        name = "include",
        in = ParameterIn.QUERY,
        description = "A comma-separated list of extra fields to include",
        schema = new Schema(
          allowableValues = Array(
            "identifiers",
            "items",
            "subjects",
            "genres",
            "contributors",
            "production",
            "notes")),
        required = false,
      ),
    )
  )
  @ApiResponse(
    responseCode = "200",
    description = "The work",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayWorkV2]))
    )
  )
  @ApiResponse(
    responseCode = "400",
    description = "Bad Request Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "404",
    description = "Not Found Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "410",
    description = "Gone Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "500",
    description = "Internal Server Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  def getWork(): Unit
}

@Path("/works")
trait MultipleWorksSwagger {

  @GET
  @Tag(name = "Works")
  @Operation(
    summary = "/works",
    description = "Returns a paginated list of works",
    parameters = Array(
      new Parameter(
        name = "include",
        in = ParameterIn.QUERY,
        description = "A comma-separated list of extra fields to include",
        schema = new Schema(
          allowableValues = Array(
            "identifiers",
            "items",
            "subjects",
            "genres",
            "contributors",
            "production",
            "notes")),
        required = false,
      ),
      new Parameter(
        name = "items.locations.locationType",
        in = ParameterIn.QUERY,
        description =
          "Filter by the LocationType of items on the retrieved works",
        required = false
      ),
      new Parameter(
        name = "workType",
        in = ParameterIn.QUERY,
        description = "Filter by the workType of the searched works",
        required = false
      ),
      new Parameter(
        name = "aggreations",
        in = ParameterIn.QUERY,
        description =
          "What aggregated data in correlation to the results should we return.",
        schema = new Schema(
          allowableValues = Array(
            "workType",
            "genres",
            "production.dates",
            "subjects",
            "language")),
        required = false
      ),
      new Parameter(
        name = "language",
        in = ParameterIn.QUERY,
        description = "Filter the work by language.",
        required = false
      ),
      new Parameter(
        name = "genres.label",
        in = ParameterIn.QUERY,
        description = "Filter the work by genre.",
        required = false
      ),
      new Parameter(
        name = "subjects.label",
        in = ParameterIn.QUERY,
        description = "Filter the work by subject.",
        required = false
      ),
      new Parameter(
        name = "license",
        in = ParameterIn.QUERY,
        description = "Filter the work by license.",
        required = false
      ),
      new Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        description = "Which field to sort the results on",
        schema = new Schema(
          allowableValues = Array("production.dates")
        ),
        required = false
      ),
      new Parameter(
        name = "sortOrder",
        in = ParameterIn.QUERY,
        description = "The order that the results should be returned in.",
        schema = new Schema(
          allowableValues = Array("asc", "desc")
        ),
        required = false
      ),
      new Parameter(
        name = "production.dates.to",
        in = ParameterIn.QUERY,
        description =
          "Return all works with a production date before and including this date.\n\nCan be used in conjunction with `production.dates.from` to create a range.",
        schema = new Schema(
          `type` = "ISO 8601 format string"
        ),
        required = false
      ),
      new Parameter(
        name = "production.dates.from",
        in = ParameterIn.QUERY,
        description =
          "Return all works with a production date after and including this date.\n\nCan be used in conjunction with `production.dates.to` to create a range.",
        schema = new Schema(
          `type` = "ISO 8601 format string"
        ),
        required = false
      ),
      new Parameter(
        name = "query",
        in = ParameterIn.QUERY,
        description =
          """Full-text search query, which will OR supplied terms by default.\n\nThe following special characters can be used to change the search behaviour:\n\n- \" wraps a number of tokens to signify a phrase for searching\n\nTo search for any of these special characters, they should be escaped with \.""",
        required = false
      ),
      new Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        description = "The page to return from the result list",
        required = false
      ),
      new Parameter(
        name = "pageSize",
        in = ParameterIn.QUERY,
        description = "The number of works to return per page (default: 10)",
        required = false
      ),
    )
  )
  @ApiResponse(
    responseCode = "200",
    description = "The works",
    content = Array(
      new Content(
        schema = new Schema(implementation = classOf[MultipleWorksResponse]))
    )
  )
  @ApiResponse(
    responseCode = "400",
    description = "Bad Request Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "404",
    description = "Not Found Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "410",
    description = "Gone Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  @ApiResponse(
    responseCode = "500",
    description = "Internal Server Error",
    content = Array(
      new Content(schema = new Schema(implementation = classOf[DisplayError]))
    )
  )
  def getWorks(): Unit
}
