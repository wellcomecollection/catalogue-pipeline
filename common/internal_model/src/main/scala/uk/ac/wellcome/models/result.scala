package uk.ac.wellcome.models.work.internal

package object result {

  type Result[T] = Either[Throwable, T]

  implicit class OptionResultOps[T](option: Option[Result[T]]) {
    def toResult: Result[Option[T]] =
      option
        .map(result => result.map(Some(_)))
        .getOrElse(Right(None))
  }

  implicit class ListResultOps[T](list: List[Result[T]]) {
    def toResult: Result[List[T]] = {
      list.collect { case Left(err) => err } match {
        case Nil  => Right(list.collect { case Right(value) => value })
        case errs => Left(new Exception(s"Multiple errors: $errs"))
      }
    }
  }
}
