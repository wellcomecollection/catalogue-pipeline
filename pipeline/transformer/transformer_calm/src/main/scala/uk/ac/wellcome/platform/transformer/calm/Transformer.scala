package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.models.work.internal.TransformedBaseWork

trait Transformer[T] {

  def transform(input: T): Either[Throwable, TransformedBaseWork]
}
