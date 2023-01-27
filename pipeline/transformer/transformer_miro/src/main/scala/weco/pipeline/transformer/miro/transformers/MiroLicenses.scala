package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.locations.License
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.exceptions.{
  ShouldNotTransformException,
  ShouldSuppressException
}

trait MiroLicenses {

  /** If the image has a non-empty image_use_restrictions field, choose which
    * license (if any) we're going to assign to the thumbnail for this work.
    *
    * The mappings in this function are based on a document provided by Christy
    * Henshaw (MIRO drop-downs.docx). There are still some gaps in that, we'll
    * have to come back and update this code later.
    *
    * For now, this mapping only covers use restrictions seen in the V
    * collection. We'll need to extend this for other licenses later.
    *
    * TODO: Expand this mapping to cover all of MIRO. TODO: Update these
    * mappings based on the final version of Christy's document.
    */
  def chooseLicense(
    maybeUseRestrictions: Option[String],
    overrides: MiroSourceOverrides
  ): License =
    overrides.license match {
      case Some(license) => license
      case None => chooseLicenseFromUseRestrictions(maybeUseRestrictions)
    }

  private def chooseLicenseFromUseRestrictions(
    maybeUseRestrictions: Option[String]
  ): License =
    maybeUseRestrictions match {

      // These images need more data.
      case None =>
        throw new ShouldNotTransformException(
          "Nothing in the image_use_restrictions field"
        )

      case Some(useRestrictions) =>
        useRestrictions match {

          // Certain strings map directly onto license types
          case "CC-0"         => License.CC0
          case "CC-BY"        => License.CCBY
          case "CC-BY-NC"     => License.CCBYNC
          case "CC-BY-NC-ND"  => License.CCBYNCND
          case "PDM"          => License.PDM
          case "In copyright" => License.InCopyright

          // These mappings are defined in Christy's document
          case "Academics" => License.CCBYNC

          // These images should really be removed entirely and sent to something
          // like Tandem Vault, but we have seen some of these strings in the
          // catalogue data -- for now, explicitly mark these as "do not transform"
          // so they don't end up on the DLQ.
          case "Do not use" =>
            throw new ShouldSuppressException(
              "image_use_restrictions = 'Do not use'"
            )
          case "Image withdrawn, see notes" =>
            throw new ShouldSuppressException(
              "image_use_restrictions = 'Image withdrawn, see notes'"
            )
        }
    }
}
