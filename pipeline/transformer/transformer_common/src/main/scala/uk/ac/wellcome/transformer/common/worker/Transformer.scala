package uk.ac.wellcome.transformer.common.worker

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.models.work.internal.result.Result

trait Transformer[T] {

  def apply(input: T, version: Int): Result[TransformedBaseWork]
}
