package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

/** An indexer for either values which defers to one of two internal indexers
  *  dependning on whether a Left or Right is received. */
class EitherIndexer[L: Indexable, R: Indexable](
  leftIndexer: Indexer[L],
  rightIndexer: Indexer[R])(implicit ec: ExecutionContext)
    extends Indexer[Either[L, R]] {

  def init(): Future[Unit] =
    leftIndexer.init().flatMap(_ => rightIndexer.init())

  def apply(documents: Seq[Either[L, R]]): Future[Either[Seq[Either[L, R]], Seq[Either[L, R]]]] = {
    val leftDocs = documents.collect { case Left(doc) => doc }
    val rightDocs = documents.collect { case Right(doc) => doc }
    for {
      leftResult <- leftIndexer(leftDocs)
      rightResult <- rightIndexer(rightDocs)
    } yield (leftResult, rightResult) match {
      case (Right(leftDocs), Right(rightDocs)) =>
        Right(leftDocs.map(Left(_)) ++ rightDocs.map(Right(_)))
      case (Left(leftDocs), Left(rightDocs)) =>
        Left(leftDocs.map(Left(_)) ++ rightDocs.map(Right(_)))
      case (Left(leftDocs), Right(_)) => Left(leftDocs.map(Left(_)))
      case (Right(_), Left(rightDocs)) => Left(rightDocs.map(Right(_)))
    }
  }
}
