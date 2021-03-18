package weco.catalogue.transformer.example

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.WorkState.Source
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.transformer.{Transformer, TransformerWorker}

import scala.concurrent.ExecutionContext

sealed trait ExampleData
case class ValidExampleData(id: SourceIdentifier, title: String)
    extends ExampleData
case object InvalidExampleData extends ExampleData

object ExampleTransformer extends Transformer[ExampleData] with WorkGenerators {
  override def apply(data: ExampleData,
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
