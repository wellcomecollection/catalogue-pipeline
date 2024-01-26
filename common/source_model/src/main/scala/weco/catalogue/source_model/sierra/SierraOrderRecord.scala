package weco.catalogue.source_model.sierra

import java.time.Instant
import weco.json.JsonUtil._
import weco.sierra.models.identifiers.{SierraBibNumber, SierraOrderNumber}

import scala.util.{Failure, Success}

case class SierraOrderRecord(
  id: SierraOrderNumber,
  data: String,
  modifiedDate: Instant,
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber] = List()
) extends AbstractSierraRecord[SierraOrderNumber]

case object SierraOrderRecord {
  private case class SierraAPIData(bibs: List[String])

  /** This apply method is for parsing JSON bodies that come from the Sierra API.
    */
  def apply(
    id: String,
    data: String,
    modifiedDate: Instant
  ): SierraOrderRecord = {
    val bibIds = fromJson[SierraAPIData](data) match {
      // The Sierra API returns bibs on orders as a list of URLs,
      // e.g. https://libsys.wellcomelibrary.org/iii/sierra-api/v6/bibs/1459609
      //
      // For now, just split on slashes and take the final part.
      case Success(apiData) =>
        apiData.bibs.map {
          url =>
            assert(
              url.startsWith(
                "https://libsys.wellcomelibrary.org/iii/sierra-api"
              )
            )
            SierraBibNumber(url.split("/").last)
        }
      case Failure(e) =>
        throw new IllegalArgumentException(
          s"Error parsing bibIds from JSON <<$data>> ($e)"
        )
    }

    SierraOrderRecord(
      id = SierraOrderNumber(id),
      data = data,
      modifiedDate = modifiedDate,
      bibIds = bibIds
    )
  }
}
