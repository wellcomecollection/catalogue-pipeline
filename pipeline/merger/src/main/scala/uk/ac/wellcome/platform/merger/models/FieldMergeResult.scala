package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.TransformedBaseWork

/*
 * FieldMergeResult is the return type of a FieldMergeRule's `merge` method
 * and contains both the new (merged) `data` for the field but also a list
 * of `sources` which values were used to compute the new value.
 * It is up to the merger how to handle these.
 */
case class FieldMergeResult[T](data: T, sources: Seq[TransformedBaseWork])
