package weco.catalogue.source_model.sierra

import java.time.Instant
import weco.json.JsonUtil._
import weco.sierra.models.identifiers.{SierraBibNumber, SierraHoldingsNumber}

import scala.util.{Failure, Success}

case class SierraHoldingsRecord(
  id: SierraHoldingsNumber,
  data: String,
  modifiedDate: Instant,
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber] = List()
) extends AbstractSierraRecord[SierraHoldingsNumber]

case object SierraHoldingsRecord {

  private case class SierraAPIData(bibIds: List[Int])

  /** This apply method is for parsing JSON bodies that come from the
    * Sierra API.
    */
  def apply(id: String,
            data: String,
            modifiedDate: Instant): SierraHoldingsRecord = {
    val bibIds = fromJson[SierraAPIData](data) match {
      case Success(apiData) =>
        apiData.bibIds.map { v =>
          SierraBibNumber(v.toString)
        }
      case Failure(e) =>
        throw new IllegalArgumentException(
          s"Error parsing bibIds from JSON <<$data>> ($e)")
    }

    SierraHoldingsRecord(
      id = SierraHoldingsNumber(id),
      data = data,
      modifiedDate = modifiedDate,
      bibIds = bibIds
    )
  }
}
