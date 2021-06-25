package weco.pipeline.transformer

package object result {

  type Result[T] = Either[Throwable, T]

  implicit class OptionResultOps[T](option: Option[Result[T]]) {
    def toResult: Result[Option[T]] =
      option
        .map(result => result.map(Some(_)))
        .getOrElse(Right(None))
  }
}
