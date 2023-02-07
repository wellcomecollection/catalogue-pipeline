package weco.pipeline.sierra_reader.flow

import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.circe.Json
import weco.pipeline.sierra_reader.parsers.SierraRecordParser
import weco.catalogue.source_model.sierra.AbstractSierraRecord

object SierraRecordWrapperFlow {
  def apply[T <: AbstractSierraRecord[_]](
    createRecord: (String, String, Instant) => T
  ): Flow[Json, T, NotUsed] =
    Flow.fromFunction(SierraRecordParser(createRecord))
}
