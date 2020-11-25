package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.MemoryRetriever

trait RetrieverFixtures {
  def givenStored(retriever: MemoryRetriever[Work[Source]], works: Work[Source]*): Unit =
    retriever.index = works.map {w => w.sourceIdentifier.toString -> w}.toMap
}
