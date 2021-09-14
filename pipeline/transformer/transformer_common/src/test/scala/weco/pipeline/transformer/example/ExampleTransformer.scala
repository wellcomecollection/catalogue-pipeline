package weco.pipeline.transformer.example

import io.circe.Decoder
import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.catalogue.source_model.CalmSourcePayload
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{Transformer, TransformerWorker}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.VersionedStore
import weco.storage.{Identified, ReadError, Version}

import scala.concurrent.ExecutionContext

sealed trait ExampleData
case class ValidExampleData(id: SourceIdentifier, title: String)
    extends ExampleData
case object InvalidExampleData extends ExampleData

object ExampleTransformer extends Transformer[ExampleData] with WorkGenerators {
  override def apply(id: String,
                     data: ExampleData,
                     version: Int): Either[Exception, Work.Visible[Source]] =
    data match {
      case ValidExampleData(id, title) =>
        Right(
          Work.Visible[Source](
            state = Source(id, modifiedTime),
            data = WorkData(
              title = Some(s"ID: $id / title=$title")
            ),
            version = version
          )
        )

      case InvalidExampleData => Left(new Exception("No No No"))
    }
}

class ExampleTransformerWorker(
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            String],
  val retriever: Retriever[Work[Source]],
  sourceStore: VersionedStore[S3ObjectLocation, Int, ExampleData]
)(
  implicit
  val decoder: Decoder[CalmSourcePayload],
  val ec: ExecutionContext
) extends TransformerWorker[CalmSourcePayload, ExampleData, String] {

  override val transformer: Transformer[ExampleData] = ExampleTransformer

  override def lookupSourceData(p: CalmSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], ExampleData]] =
    sourceStore
      .getLatest(p.location)
      .map {
        case Identified(Version(_, v), data) =>
          Identified(Version(p.id, v), data)
      }
}
