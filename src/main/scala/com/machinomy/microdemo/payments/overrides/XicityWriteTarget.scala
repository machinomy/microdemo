package com.machinomy.microdemo.payments.overrides

import com.machinomy.microdemo.communication.Peer
import com.machinomy.xicity.Identifier
import org.bitcoinj.net.MessageWriteTarget

class XicityWriteTarget(identifier: Identifier, protocol: Long) extends MessageWriteTarget {
  override def writeBytes(message: Array[Byte]): Unit = {
    Peer().send(identifier, protocol, message)
  }

  override def closeConnection(): Unit = {
    // pass
  }
}
