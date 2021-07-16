package weco.catalogue.tei.id_extractor.fixtures

import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

trait LocalResources {
  def readResource(name: String): String =
    IOUtils.resourceToString(name, StandardCharsets.UTF_8)
}
