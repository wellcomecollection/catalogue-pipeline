package weco.pipeline.transformer.tei

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{DeletedReason, Work, WorkState}
import weco.catalogue.source_model.tei.{
  TeiChangedMetadata,
  TeiDeletedMetadata,
  TeiMetadata
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.result.Result
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Readable

import java.time.Instant

class TeiTransformer(teiReader: Readable[S3ObjectLocation, String])
    extends Transformer[TeiMetadata] {
  override def apply(
    id: String,
    sourceData: TeiMetadata,
    version: Int
  ): Result[Work[WorkState.Source]] =
    sourceData match {
      case TeiChangedMetadata(s3Location, time) =>
        handleTeiChange(id, version, s3Location, time)
      case TeiDeletedMetadata(time) =>
        handleTeiDelete(id, version, time)
    }

  /** A deletion has no XML to parse, so the inner works it used to have are
    * only knowable from the previously stored work. The merger needs them to
    * delete the children.
    */
  override def reconcileWithStored(
    newWork: Work[Source],
    storedWork: Work[Source]
  ): Work[Source] =
    newWork match {
      case w: Work.Deleted[Source] if w.state.internalWorkStubs.isEmpty =>
        w.copy(
          state = w.state.copy(
            internalWorkStubs = storedWork.state.internalWorkStubs
          )
        )
      case _ => newWork
    }

  /** Sending a deletion without its stubs loses them for good: it overwrites
    * the stored work that held them, so a replay has nothing left to read.
    */
  override def requiresStoredWork(newWork: Work[Source]): Boolean =
    newWork match {
      case w: Work.Deleted[Source] => w.state.internalWorkStubs.isEmpty
      case _                       => false
    }

  private def handleTeiDelete(id: String, version: Int, time: Instant) =
    Right(
      Work.Deleted[Source](
        version = version,
        state = Source(SourceIdentifier(IdentifierType.Tei, "Work", id), time),
        deletedReason = DeletedReason.DeletedFromSource("Deleted by TEI source")
      )
    )

  private def handleTeiChange(
    id: String,
    version: Int,
    s3Location: S3ObjectLocation,
    time: Instant
  ): Result[Work[Source]] =
    for {
      xmlString <- teiReader.get(s3Location).left.map(_.e)
      teiXml <- TeiXml(id, xmlString.identifiedT)
      teiData <- teiXml.parse
    } yield teiData.toWork(time, version)

}
