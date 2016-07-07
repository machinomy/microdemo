package com.machinomy.microdemo.payments.overrides

import java.net.SocketAddress

import com.machinomy.xicity.Identifier

case class XicityAddress(identifier: Identifier) extends SocketAddress {

  def getBytes: Array[Byte] = {
    identifier.toString.getBytes()
  }

}
