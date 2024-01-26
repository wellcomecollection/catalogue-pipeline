package weco.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

/** An indexer for either values which defers to one of two internal indexers dependning on whether
  * a Left or Right is received.
  */
class EitherIndexer[L: Indexable, R: Indexable](
  leftIndexer: Indexer[L],
  rightIndexer: Indexer[R]
)(implicit ec: ExecutionContext)
    extends Indexer[Either[L, R]] {

  type Result[T] = Either[Seq[T], Seq[T]]

  def init(): Future[Unit] =
    for {
      _ <- leftIndexer.init()
      _ <- rightIndexer.init()
    } yield ()

  def apply(docs: Seq[Either[L, R]]): Future[Result[Either[L, R]]] = {
    val leftDocs = docs.collect { case Left(doc) => doc }
    val rightDocs = docs.collect { case Right(doc) => doc }
    for {
      leftResult <- index(leftIndexer, leftDocs)
      rightResult <- index(rightIndexer, rightDocs)
    } yield errors(leftResult, rightResult) match {
      case Nil    => Right(successes(leftResult, rightResult))
      case errors => Left(errors)
    }
  }

  def index[T](indexer: Indexer[T], docs: Seq[T]): Future[Result[T]] =
    docs match {
      case Nil  => Future.successful(Right(Nil))
      case docs => indexer(docs)
    }

  def errors(leftResult: Result[L], rightResult: Result[R]): Seq[Either[L, R]] =
    errors(leftResult).map(Left(_)) ++ errors(rightResult).map(Right(_))

  def errors[T](result: Result[T]): Seq[T] =
    result match {
      case Left(errors) => errors
      case Right(_)     => Nil
    }

  def successes(
    leftResult: Result[L],
    rightResult: Result[R]
  ): Seq[Either[L, R]] =
    successes(leftResult).map(Left(_)) ++ successes(rightResult).map(Right(_))

  def successes[T](result: Result[T]): Seq[T] =
    result match {
      case Left(_)          => Nil
      case Right(successes) => successes
    }
}
