package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  ImageData,
  MergedImage,
  TransformedBaseWork,
  UnidentifiedWork,
  Unminted
}
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
import scala.Function.const

object ImagesRule extends FieldMergeRule {
  type FieldData = List[MergedImage[Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
    FieldMergeResult(
      fieldData =
        getMetsImages.applyOrElse((target, sources), const(Nil)) ++
          getMiroImages.applyOrElse((target, sources), const(Nil)),
      redirects = Nil
    )

  private lazy val getMetsImages = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.metsWork

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): List[MergedImage[Unminted]] =
      sources.flatMap {
        _.data.images.map {
          _.mergeWith(
            ImageData()
          )
        }
      }.toList
  }

  private lazy val getMiroImages = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.miroWork

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): List[MergedImage[Unminted]] =
      sources.flatMap {
        _.data.images.map {
          _.mergeWith(
            ImageData()
          )
        }
      }.toList
  }
}
