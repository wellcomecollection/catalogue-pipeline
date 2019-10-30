package uk.ac.wellcome.platform.transformer.mets.service

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

class TokenService(url: String, clientId: String, secret: String) {
  def getNewToken(): Future[String] = ???

  private val token = new AtomicReference[String]("")
  def getCurrentToken: String = token.get()

}
