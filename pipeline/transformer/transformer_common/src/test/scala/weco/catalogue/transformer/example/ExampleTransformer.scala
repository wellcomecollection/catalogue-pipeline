package weco.catalogue.transformer.example

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal.{SourceIdentifier, Work}
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import weco.catalogue.transformer.{Transformer, TransformerWorker}

sealed trait ExampleData
case class ValidExampleData(id: SourceIdentifier) extends ExampleData
case object InvalidExampleData extends ExampleData

object ExampleTransformer extends Transformer[ExampleData] with WorkGenerators {
  def apply(data: ExampleData,
            version: Int): Either[Exception, Work.Visible[Source]] =
    data match {
      case ValidExampleData(id) => Right(sourceWork(id).withVersion(version))
      case InvalidExampleData   => Left(new Exception("No No No"))
    }
}

class ExampleTransformerWorker(
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            String],
  sourceStore: VersionedStore[String, Int, ExampleData]
) extends TransformerWorker[ExampleData, String] {

  override val transformer: Transformer[ExampleData] = ExampleTransformer

  override def lookupSourceData(id: String)
    : Either[ReadError, Identified[Version[String, Int], ExampleData]] =
    sourceStore.getLatest(id)
}
