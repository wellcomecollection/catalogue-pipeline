package uk.ac.wellcome.platform.merger.model
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}

case class MergedWork(work: UnidentifiedWork,
                      redirectedWorks: Seq[UnidentifiedRedirectedWork])

object MergedWork {
  def apply(work: UnidentifiedWork,
            redirectedWork: UnidentifiedRedirectedWork): MergedWork =
    MergedWork(work, Seq(redirectedWork))
}

case class PotentialMergedWork(target: UnidentifiedWork,
                               redirectedWorks: Seq[TransformedBaseWork])

object PotentialMergedWork {
  def apply(target: UnidentifiedWork,
            redirectedWork: TransformedBaseWork): PotentialMergedWork =
    PotentialMergedWork(target, Seq(redirectedWork))
}
