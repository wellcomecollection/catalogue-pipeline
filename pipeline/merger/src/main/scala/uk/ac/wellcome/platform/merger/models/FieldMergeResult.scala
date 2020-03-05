package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.TransformedBaseWork

/*
 * FieldMergeResult is the return type of a FieldMergeRule's `merge` method
 * and contains both the new (merged) value for the field but also a list
 * of sources which the rule would like the merger to redirect. It is up to
 * the merger how to handle these.
 */
case class FieldMergeResult[T](fieldData: T,
                               redirects: Seq[TransformedBaseWork])
