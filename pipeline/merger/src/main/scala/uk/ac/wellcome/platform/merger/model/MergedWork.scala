package uk.ac.wellcome.platform.merger.model
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}

case class MergedWork(work: UnidentifiedWork,
                      redirectedWork: UnidentifiedRedirectedWork)
case class PotentialMergedWork(target: UnidentifiedWork,
                               redirectedWork: TransformedBaseWork)
