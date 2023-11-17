package weco.pipeline.ingestor.models

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.json.JsonUtil._

import scala.io.Source
trait IngestorTestData {
  lazy val testWork: Work.Visible[WorkState.Denormalised] =
    fromJson[Work.Visible[WorkState.Denormalised]](
      Source.fromResource("c4zj63fx-denormalised.json").mkString
    ).get
}
