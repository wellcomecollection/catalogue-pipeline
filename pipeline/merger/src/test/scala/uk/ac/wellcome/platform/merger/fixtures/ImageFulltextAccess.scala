package uk.ac.wellcome.platform.merger.fixtures

import org.scalatest.PrivateMethodTester
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.rules.ImagesRule

trait ImageFulltextAccess extends PrivateMethodTester {
  private val fulltextMethod = PrivateMethod[Option[String]]('createFulltext)
  val createFulltext: Seq[TransformedBaseWork] => Option[String] =
    ImagesRule invokePrivate fulltextMethod(_)
}
