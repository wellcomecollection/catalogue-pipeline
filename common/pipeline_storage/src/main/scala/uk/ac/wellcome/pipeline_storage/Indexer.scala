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

  def weight(document: T): Long
}

object Indexable extends Logging {

  implicit def imageIndexable[State <: ImageState]: Indexable[Image[State]] =
    new Indexable[Image[State]] {
      def id(image: Image[State]): String = image.id

      def version(image: Image[State]) =
        image.modifiedTime.toEpochMilli

      def weight(image: Image[State]): Long =
        1
    }

  implicit def workIndexable[State <: WorkState]: Indexable[Work[State]] =
    new Indexable[Work[State]] {

      def id(work: Work[State]): String = work.id

      def version(work: Work[State]) =
        work.state.modifiedTime.toEpochMilli

      def weight(work: Work[State]): Long =
        // As an estimate here we assume 50 relations (which each consist of a
        // few key fields) is approximately the size of all the other data in a
        // single complete work. For example there are some works with around
        // 4000 relations, in which cases they will be considered to be
        // equivilent to around 80 works without any relations.
        Math.round(
          1.0 + (work.state.relations.size / 50.0)
        )
    }
}
