package uk.ac.wellcome.platform.transformer.tei.transformer

import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Store
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.tei.TeiMetadata
import weco.catalogue.transformer.Transformer
import weco.catalogue.transformer.result.Result

class TeiTransformer(store: Store[S3ObjectLocation, String]) extends Transformer[TeiMetadata]{
  override def apply(id: String, sourceData: TeiMetadata, version: Int): Result[Work[WorkState.Source]] = ???
}
