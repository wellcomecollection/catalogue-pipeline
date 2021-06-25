package weco.pipeline.transformer.mets.fixtures

import org.apache.commons.io.IOUtils

trait LocalResources {
  def loadXmlFile(path: String): String =
    IOUtils.toString(getClass.getResourceAsStream(path), "UTF-8")
}
