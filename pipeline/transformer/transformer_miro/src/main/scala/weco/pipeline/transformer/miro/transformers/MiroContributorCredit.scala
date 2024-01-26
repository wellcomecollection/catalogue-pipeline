package weco.pipeline.transformer.miro.transformers

import weco.pipeline.transformer.miro.source.MiroRecord

object MiroContributorCredit extends MiroContributorCodes {

  /** Quoting an email from Sabrina Tanner, 12 October 2022:
    *
    * The 3 letter contributor code was a unique identifier applied to an image creator’s overall
    * account (contributor account). All of their images would sit within this account. When
    * creating the contributor record, a credit line was specified and this was automatically
    * applied to all images in this account.
    *
    * For individual images, we had the option of specifying a different credit line, which is the
    * ‘credit’ field in the image record. This would override the credit line supplied by the
    * contributor record.
    *
    * So for image credits, we use the image_credit_line if available, otherwise we fall back to the
    * image_source_code.
    */
  def getCredit(miroRecord: MiroRecord): Option[String] = {
    miroRecord.creditLine match {

      // Some of the credit lines are inconsistent or use old names for
      // Wellcome, so we do a bunch of replacements and trimming to tidy
      // them up.
      case Some(line) =>
        Some(
          line
            .replaceAll(
              "Adrian Wressell, Heart of England NHSFT",
              "Adrian Wressell, Heart of England NHS FT"
            )
            .replaceAll(
              "Andrew Dilley,Jane Greening & Bruce Lynn",
              "Andrew Dilley, Jane Greening & Bruce Lynn"
            )
            .replaceAll(
              "Andrew Dilley,Nicola DeLeon & Bruce Lynn",
              "Andrew Dilley, Nicola De Leon & Bruce Lynn"
            )
            .replaceAll(
              "Ashley Prytherch, Royal Surrey County Hospital NHS Foundation Trust",
              "Ashley Prytherch, Royal Surrey County Hospital NHS FT"
            )
            .replaceAll(
              "David Gregory & Debbie Marshall",
              "David Gregory and Debbie Marshall"
            )
            .replaceAll(
              "David Gregory&Debbie Marshall",
              "David Gregory and Debbie Marshall"
            )
            .replaceAll("Geraldine Thompson.", "Geraldine Thompson")
            .replaceAll("John & Penny Hubley.", "John & Penny Hubley")
            .replaceAll(
              "oyal Army Medical Corps Muniment Collection, Wellcome Images",
              "Royal Army Medical Corps Muniment Collection, Wellcome Collection"
            )
            .replaceAll("Science Museum London", "Science Museum, London")
            .replaceAll("The Wellcome Library, London", "Wellcome Collection")
            .replaceAll("Wellcome Library, London", "Wellcome Collection")
            .replaceAll("Wellcome Libary, London", "Wellcome Collection")
            .replaceAll("Wellcome LIbrary, London", "Wellcome Collection")
            .replaceAll("Wellcome Images", "Wellcome Collection")
            .replaceAll("The Wellcome Library", "Wellcome Collection")
            .replaceAll("Wellcome Library", "Wellcome Collection")
            .replaceAll("Wellcome Collection London", "Wellcome Collection")
            .replaceAll("Wellcome Collection, Londn", "Wellcome Collection")
            .replaceAll("Wellcome Trust", "Wellcome Collection")
            .replaceAll("'Wellcome Collection'", "Wellcome Collection")
        )

      // Otherwise we carry through the contributor codes, which have
      // already been edited for consistency.
      case None =>
        miroRecord.sourceCode match {
          case Some(code) =>
            lookupContributorCode(miroId = miroRecord.imageNumber, code = code)
          case None => None
        }
    }
  }
}
