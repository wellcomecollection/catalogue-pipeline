package uk.ac.wellcome.platform.transformer.calm

trait Transformer[In, Out] {
  def transform(in: In): Either[Throwable, Out]
}



