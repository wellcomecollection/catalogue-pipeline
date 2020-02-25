package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  MergedImage,
  TransformedBaseWork,
  UnidentifiedWork,
  Unminted
}
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

object ImagesRule extends FieldMergeRule {
  type FieldData = List[MergedImage[Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = ???
}
