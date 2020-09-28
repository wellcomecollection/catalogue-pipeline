package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future
import scala.language.implicitConversions

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

object Indexable {

  implicit val imageIndexable: Indexable[AugmentedImage] =
    new Indexable[AugmentedImage] {
      def id(image: AugmentedImage) =
        image.id.canonicalId
      def version(image: AugmentedImage) =
        image.version
    }

  implicit val workIndexable: Indexable[Work[Identified]] =
    new Indexable[Work[Identified]] {

      def id(work: Work[Identified]) =
        work.state.canonicalId

      /**
        * When the merger makes the decision to merge some works, it modifies
        * the content of the affected works. Despite the content of these works
        * being modified, their version remains the same. Instead, the merger
        * sets the merged flag to true for the target work and changes the type
        * of the other works to redirected.
        *
        * When we ingest those works, we need to make sure that the merger
        * modified works are never overridden by unmerged works for the same
        * version still running through the pipeline.
        * We also need to make sure that, if a work is modified by a source in
        * such a way that it shouldn't be merged (or redirected) anymore, the
        * new unmerged version is ingested and never replaced by the previous
        * merged version still running through the pipeline.
        *
        * We can do that by ingesting works into Elasticsearch with a version
        * derived by a combination of work version, merged flag and work type.
        * More specifically, by multiplying the work version by 10, we make sure
        * that a new version of a work always wins over previous versions
        * (merged or unmerged). We make sure that a merger modified work always
        * wins over other works with the same version, by adding one to
        * work.version * 10.
        */
      def version(work: Work[Identified]) =
        work match {
          case Work.Visible(version, data, state) => (version * 10) + state.isMerged
          case Work.Redirected(version, _, _)     => (version * 10) + 1
          case Work.Invisible(version, _, _, _)   => version * 10
        }

      implicit private def toInteger(bool: Boolean): Int = if (bool) 1 else 0
    }
}
