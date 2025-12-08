package weco.pipeline_storage

import scala.concurrent.Future
import grizzled.slf4j.Logging

import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.work.{Work, WorkState}

abstract class Indexer[T: Indexable] {

  protected val indexable: Indexable[T] = implicitly

  def init(): Future[Unit]

  /** Indexes the given documents into the store
    *
    * @param documents
    *   The documents to be indexed
    * @return
    *   A future either containing a Left with the failed documents or a Right
    *   with the successfully indexed documents
    */
  def apply(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]]

  def apply(document: T): Future[Either[Seq[T], Seq[T]]] =
    apply(documents = Seq(document))
}

trait Indexable[T] {
  def id(document: T): String

  def version(document: T): Long

  def weight(document: T): Long = 1
}

object Indexable extends Logging {

  implicit def imageIndexable[State <: ImageState]: Indexable[Image[State]] =
    new Indexable[Image[State]] {
      def id(image: Image[State]): String = image.id

      def version(image: Image[State]) =
        image.modifiedTime.toEpochMilli
    }

  implicit def workIndexable[State <: WorkState]: Indexable[Work[State]] =
    new Indexable[Work[State]] {

      def id(work: Work[State]): String = work.id

      def version(work: Work[State]) =
        work.state.modifiedTime.toEpochMilli
    }

  implicit def eitherIndexable[L: Indexable, R: Indexable]
    : Indexable[Either[L, R]] =
    new Indexable[Either[L, R]] {
      def id(either: Either[L, R]): String =
        either match {
          case Left(left)   => implicitly[Indexable[L]].id(left)
          case Right(right) => implicitly[Indexable[R]].id(right)
        }

      def version(either: Either[L, R]) =
        either match {
          case Left(left)   => implicitly[Indexable[L]].version(left)
          case Right(right) => implicitly[Indexable[R]].version(right)
        }
    }
}
