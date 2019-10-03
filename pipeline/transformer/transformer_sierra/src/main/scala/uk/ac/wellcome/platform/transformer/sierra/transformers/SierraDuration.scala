package uk.ac.wellcome.platform.transformer.sierra.transformers

import scala.util.Try
import scala.concurrent.duration._

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

object SierraDuration extends SierraTransformer with MarcUtils {

  type Output = Option[Int]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getFirstSubfieldContent(bibData, "306", "a")
      .map { durationString =>
        durationString
          .grouped(2)
          .map(substr => Try(substr.toInt).toOption)
          .toSeq
      }
      .collect {
        case Seq(Some(hours), Some(minutes), Some(seconds)) =>
          (hours.hours + minutes.minutes + seconds.seconds).toSeconds.toInt
      }
}
