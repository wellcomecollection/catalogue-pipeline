package weco.pipeline.transformer.tei

object NormaliseText {
  def apply(s: String): Option[String] = {
    val result =
      s.collapseNewlines

    if (result.nonEmpty) Some(result) else None
  }

  private implicit class TextOps(s: String) {

    // Sometimes an XML value is split across multiple lines, but that's for the
    // benefit of the human reader and isn't actually meaningful.  e.g.
    //
    //      <colophon><locus>F.44v </locus>iti śrīupadhyānaśrutādhyene cau̎tho
    //        uddeśo saṃpūrṇaṃ sarvadhyenaṃ navamaṃ samāptaṃ 9 iti śrīācārarṇga
    //        paḍhamo suyakhaṃdho sammattaṃ viśāṣakṛṣṇā catuthīṃ dine vudhavāsare
    //        pūrṇaṃ kṛtaṃ</colophon>
    //
    // We should collapse these into a single line.
    def collapseNewlines: String =
      s
        .split("\n")
        .map(_.trim)
        .mkString(" ")
  }
}
