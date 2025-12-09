package weco.pipeline.ingestor.models

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.json.JsonUtil._

import scala.io.Source
trait IngestorTestData {
  lazy val testWork: Work.Visible[WorkState.Merged] =
    fromJson[Work.Visible[WorkState.Merged]](
      Source.fromResource("c4zj63fx-merged.json").mkString
    ).get
}
