package weco.catalogue.source_model.sierra

import java.time.Instant
import weco.json.JsonUtil._
import weco.sierra.models.identifiers.{SierraBibNumber, SierraItemNumber}

import scala.util.{Failure, Success}

case class SierraItemRecord(
  id: SierraItemNumber,
  data: String,
  modifiedDate: Instant,
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber] = List()
) extends AbstractSierraRecord[SierraItemNumber]

case object SierraItemRecord {

  private case class SierraAPIData(bibIds: List[String])

  /** This apply method is for parsing JSON bodies that come from the Sierra
    * API.
    */
  def apply(
    id: String,
    data: String,
    modifiedDate: Instant
  ): SierraItemRecord = {
    val bibIds = fromJson[SierraAPIData](data) match {
      case Success(apiData) => apiData.bibIds
      case Failure(e) =>
        throw new IllegalArgumentException(
          s"Error parsing bibIds from JSON <<$data>> ($e)"
        )
    }

    SierraItemRecord(
      id = SierraItemNumber(id),
      data = data,
      modifiedDate = modifiedDate,
      bibIds = bibIds.map { new SierraBibNumber(_) }
    )
  }
}
