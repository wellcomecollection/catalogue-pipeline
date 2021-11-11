package weco.pipeline.transformer.tei

object NormaliseText {
  def apply(s: String): Option[String] = {
    val result =
      s
        .removeHtmlComments
        .trim

    if (result.nonEmpty) Some(result) else None
  }

  private implicit class TextOps(s: String) {
    // The TEI templates include HTML comments explaining how a particular tag
    // should be used, e.g.
    //
    //      <explicit><!-- insert --> <locus></locus>
    //        <!-- Use <note> to add general comment -->
    //      </explicit>
    //
    // We don't want to expose these in the API, or create fields that just contain
    // comments, so this function removes any comments from a string.
    //
    // TODO: Use a proper HTML parser here.  Needs more discussion -- do we
    // want *any* HTML elements coming through?  If not, we could just run strings
    // through an HTML parser and get the visible text.
    def removeHtmlComments: String =
      s.replaceAll("<!-- [a-zA-Z0-9\\s<>+'] -->", "")
  }
}
