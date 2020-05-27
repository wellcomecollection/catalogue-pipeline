package uk.ac.wellcome.platform.transformer.sierra.flgfadk

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.models.work.internal.result.Result

trait Transformer[T] {

  def apply(input: T, version: Int): Result[TransformedBaseWork]
}
