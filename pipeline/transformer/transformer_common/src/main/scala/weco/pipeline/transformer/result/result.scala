package weco.pipeline.transformer

package object result {
  type Result[T] = Either[Throwable, T]
}
