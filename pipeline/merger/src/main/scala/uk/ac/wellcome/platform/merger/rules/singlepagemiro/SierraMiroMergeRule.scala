package uk.ac.wellcome.platform.merger.rules.singlepagemiro

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.{MergerRule, WorkPairMerger}

/** If we have a Miro work and a Sierra work with a single item,
  * the Sierra work replaces the Miro work (because this is metadata
  * that we can update).
  *
  * Specifically:
  *
  *   - We copy across all the identifiers from the Miro work (except
  *     those which contain Sierra identifiers)
  *
  *   - We combine the locations on the items, and use the Miro iiif-image
  *     location for the thumbnail.
  *
  */
object SierraMiroMergeRule
    extends MergerRule
    with Logging
    with MergerLogging
    with WorkPairMerger
    with SierraMiroPartitioner {

  override protected def mergeAndRedirectWorkPair(
    sierraWork: UnidentifiedWork,
    miroWork: TransformedBaseWork): Option[MergedWork] = {
    (sierraWork.data.items, miroWork.data.items) match {
      case (
          List(sierraItem: Item[Unminted]),
          List(miroItem @ Item(Unidentifiable, _, _, _))) =>
        info(s"Merging ${describeWorkPair(sierraWork, miroWork)}.")

        val mergedWork = sierraWork.withData { data =>
          data.copy(
            otherIdentifiers = mergeIdentifiers(sierraWork, miroWork),
            items = mergeItems(sierraItem, miroItem),
            // We always copy across the thumbnail from the Miro work, at least
            // for now -- it's never populated on Sierra, always populated in Miro.
            // Later we may use the iiif-presentation item location to populate
            // this field, but right now it's empty on all Sierra works.
            thumbnail = miroWork.data.thumbnail
          )
        }

        Some(
          MergedWork(
            mergedWork,
            UnidentifiedRedirectedWork(
              version = miroWork.version,
              sourceIdentifier = miroWork.sourceIdentifier,
              redirect = IdentifiableRedirect(sierraWork.sourceIdentifier),
            )
          )
        )
      case _ =>
        None
    }
  }

  private def mergeItems(sierraItem: Item[Unminted],
                         miroItem: Item[Unminted]): List[Item[Unminted]] =
    // We always use the locations from the Sierra and the Miro records.
    //
    // We may sometimes have digital locations from both records:
    //
    //   * a iiif-image location from Miro
    //   * a iiif-presentation location from Sierra
    //
    // This is when an image has been through the digitisation workflow
    // after it came from Sierra.  We may remove the iiif-image later
    // (strictly speaking the -presentation replaces it), but we leave
    // it for now, so the website can still use it.
    List(
      sierraItem.copy(
        locations = sierraItem.locations ++ miroItem.locations
      )
    )

  /**
    *  Exclude all Sierra identifiers from the Miro work when
    *  merging, not just the identifer to the Sierra merge target.
    *  This is because in some cases Miro works have incorrect Sierra
    *  identifiers and there is no way to edit them, so they are
    *  dropped here.
    */
  val doNotMergeIdentifierTypes =
    List("sierra-identifier", "sierra-system-number")
  private def mergeIdentifiers(sierraWork: UnidentifiedWork,
                               miroWork: TransformedBaseWork) = {
    sierraWork.otherIdentifiers ++
      miroWork.identifiers.filterNot(sourceIdentifier =>
        doNotMergeIdentifierTypes.contains(sourceIdentifier.identifierType.id))
  }
}
