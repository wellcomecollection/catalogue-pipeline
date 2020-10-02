package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future

import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

abstract class Indexer[T: Indexable] {

  protected val indexable: Indexable[T] = implicitly

  /** Indexes the given documents into the store
    *
    * @param documents The documents to be indexed
    * @return A future either containing a Left with the failed documents or a
    *         Right with the succesfully indexed documents
    */
  def index(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]]
}

trait Indexable[T] {
  def id(document: T): String

  def version(document: T): Int
}

/**
  * Versioning:
  *
  * When the merger makes the decision to merge some works, it modifies
  * the content of the affected works. Despite the content of these works
  * being modified, their version remains the same. However, the merger
  * attaches the number of merged sources (`nMergedSources`) to the target
  * work and may choose to redirect some of the source works.
  *
  * When we ingest those works, we need to make sure that the merger
  * modified works are never overridden by unmerged works for the same
  * version still running through the pipeline.
  *
  * We also need to make sure that, if a work is modified by a source in
  * such a way that it shouldn't be merged (or redirected) anymore, the
  * new unmerged version is ingested and never replaced by the previous
  * merged version still running through the pipeline.
  *
  * We can do that by ingesting works into Elasticsearch with a document
  * version derived from a combination of work version, number of merged
  * sources and work type.
  *
  * More specifically, by multiplying the work version by 10, we make sure
  * that a new version of a work always wins over previous versions
  * (merged or unmerged, provided that there are fewer than 10 merged
  * sources, which we think is a reasonable assumption).
  *
  * We make sure that a merger modified work always wins over other works
  * with the same version, by adding the number of merged sources to
  * work.version * 10.
  *
  * The exact same logic is applied to image versions, using both the
  * merge state and the version numbers of the image's SourceWorks.
  */
object Indexable {

  implicit val imageIndexable: Indexable[AugmentedImage] =
    new Indexable[AugmentedImage] {
      def id(image: AugmentedImage) =
        image.id.canonicalId

      def version(image: AugmentedImage) =
        10 * (image.version + image.source.version) + (image.source match {
          case SourceWorks(_, Some(_), nMergedSources) => nMergedSources + 1
          case SourceWorks(_, None, nMergedSources)    => nMergedSources
          case _                                       => 0
        })
    }

  implicit val workIndexable: Indexable[Work[Identified]] =
    new Indexable[Work[Identified]] {

      def id(work: Work[Identified]) =
        work.state.canonicalId

      def version(work: Work[Identified]) =
        work match {
          case Work.Visible(version, _, state) =>
            (version * 10) + state.nMergedSources
          case Work.Redirected(version, _, _)   => (version * 10) + 1
          case Work.Invisible(version, _, _, _) => version * 10
        }
    }
}
