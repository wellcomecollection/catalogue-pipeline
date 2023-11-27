package weco.pipeline.transformer.mets.transformers

trait DLCSFilenameNormaliser {

  /** Filenames in DLCS are always prefixed with the bnumber (uppercase or
    * lowercase) to ensure uniqueness. However they might not be prefixed with
    * the bnumber in the METS file. So we need to do two things:
    *   - strip the "objects/" part of the location
    *   - prepend the bnumber followed by an underscore if it's not already
    *     present (uppercase or lowercase)
    */
  protected def normaliseLocation(
    bNumber: String,
    fileLocation: String
  ): String =
    fileLocation.replaceFirst("objects/", "") match {
      case fileName if fileName.toLowerCase.startsWith(bNumber.toLowerCase) =>
        fileName
      case fileName => s"${bNumber}_$fileName"
    }
}
