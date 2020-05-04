package uk.ac.wellcome.platform.sierra_reader.flow

import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.circe.Json
import uk.ac.wellcome.platform.sierra_reader.parsers.SierraRecordParser
import uk.ac.wellcome.sierra_adapter.model.AbstractSierraRecord

object SierraRecordWrapperFlow {
  def apply[T <: AbstractSierraRecord](
    createRecord: (String, String, Instant) => T): Flow[Json, T, NotUsed] =
    Flow.fromFunction(SierraRecordParser(createRecord))
}
