package weco.pipeline.transformer.example

import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.catalogue.source_model.CalmSourcePayload
import weco.pipeline.transformer.{SourceDataRetriever, Transformer}
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.VersionedStore
import weco.storage.{Identified, ReadError, Version}

sealed trait ExampleData
case class ValidExampleData(id: SourceIdentifier, title: String)
    extends ExampleData
case object InvalidExampleData extends ExampleData

class ExampleTransformer extends Transformer[ExampleData] with WorkGenerators {
  override def apply(
    id: String,
    data: ExampleData,
    version: Int
  ): Either[Exception, Work.Visible[Source]] =
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

class ExampleSourcePayloadLookup(
  sourceStore: VersionedStore[S3ObjectLocation, Int, ExampleData]
) extends SourceDataRetriever[CalmSourcePayload, ExampleData] {
  override def lookupSourceData(
    p: CalmSourcePayload
  ): Either[ReadError, Identified[Version[String, Int], ExampleData]] =
    sourceStore
      .getLatest(p.location)
      .map {
        case Identified(Version(_, v), data) =>
          Identified(Version(p.id, v), data)
      }
}
