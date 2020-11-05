package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future
import grizzled.slf4j.Logging

import uk.ac.wellcome.models.work.internal._

abstract class Indexer[T: Indexable] {

  protected val indexable: Indexable[T] = implicitly

  def init(): Future[Unit]

  /** Indexes the given documents into the store
    *
    * @param documents The documents to be indexed
    * @return A future either containing a Left with the failed documents or a
    *         Right with the succesfully indexed documents
    */
  def index(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]]

  def index(document: T): Future[Either[Seq[T], Seq[T]]] =
    index(documents = Seq(document))
}

trait Indexable[T] {
  def id(document: T): String

  def version(document: T): Long
}

object Indexable extends Logging {

  implicit val imageIndexable: Indexable[AugmentedImage] =
    new Indexable[AugmentedImage] {
      def id(image: AugmentedImage) =
        image.id.canonicalId

      def version(image: AugmentedImage) =
        image.modifiedTime.toEpochMilli
    }

  implicit def workIndexable[State <: WorkState]: Indexable[Work[State]] =
    new Indexable[Work[State]] {

      def id(work: Work[State]): String = work.id

      def version(work: Work[State]) =
        work.state.modifiedTime.toEpochMilli

    }
}
