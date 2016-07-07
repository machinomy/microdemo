package com.machinomy.microdemo.payments.overrides

import java.nio.ByteBuffer

import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import com.machinomy.xicity.Identifier
import org.bitcoin.paymentchannel.Protos
import org.bitcoinj.core.{Coin, Sha256Hash, TransactionBroadcaster, Wallet}
import org.bitcoinj.net.ProtobufParser
import org.bitcoinj.protocols.channels.{PaymentChannelCloseException, PaymentChannelServer}

class XicityPaymentChannelServerListener(broadcaster: TransactionBroadcaster, wallet: Wallet, timeout: Int, minAcceptedChannelSize: Coin, eventHandlerFactory: XicityHandlerFactory) {

  val socketProtobufHandler = new ProtobufParser[Protos.TwoWayChannelMessage](protobufHandlerListener, Protos.TwoWayChannelMessage.getDefaultInstance, Short.MaxValue, 15 * 1000)
  var closeReason: PaymentChannelCloseException.CloseReason = null
  var eventHandler: XicityServerConnectionEventHandler = null

  lazy val paymentChannelManager = new PaymentChannelServer(broadcaster, wallet, minAcceptedChannelSize, new PaymentChannelServer.ServerConnection() {
    def sendToClient(msg: Protos.TwoWayChannelMessage) {
      socketProtobufHandler.write(msg)
    }

    def destroyConnection(reason: PaymentChannelCloseException.CloseReason) {
      if (closeReason != null) closeReason = reason
      socketProtobufHandler.closeConnection()
    }

    def channelOpen(contractHash: Sha256Hash) {
      socketProtobufHandler.setSocketTimeout(0)
      eventHandler.channelOpen(contractHash)
    }

    def paymentIncrease(by: Coin, to: Coin, info: ByteString = null): ListenableFuture[ByteString] = {
      eventHandler.paymentIncrease(by, to, info)
    }
  })

  lazy val protobufHandlerListener: ProtobufParser.Listener[Protos.TwoWayChannelMessage] = new ProtobufParser.Listener[Protos.TwoWayChannelMessage]() {
    def messageReceived(handler: ProtobufParser[Protos.TwoWayChannelMessage], msg: Protos.TwoWayChannelMessage) {
      println(s"^^^^^^^^^^^^^^^$msg")
      paymentChannelManager.receiveMessage(msg)
    }

    def connectionClosed(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
      paymentChannelManager.connectionClosed()
      if (closeReason != null) eventHandler.channelClosed(closeReason)
      else eventHandler.channelClosed(PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED)
    }

    def connectionOpen(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
      // TODO: ???
//      println("$$$$$$$$$$$$$$$$$$")
//      val newEventHandler: XicityServerConnectionEventHandler = eventHandlerFactory.onNewConnection(new XicityAddress(new Identifier(128)))
//      Option(newEventHandler) match {
//        case Some(value) =>
//          eventHandler = newEventHandler
//          paymentChannelManager.connectionOpen()
//
//        case None =>
//          handler.closeConnection()
//      }
    }
  }

  def ready(identifier: Identifier) = {
    println("CONNECTED ~~~~~~~~~~")
    val newEventHandler: XicityServerConnectionEventHandler = eventHandlerFactory.onNewConnection(new XicityAddress(identifier))
    eventHandler = newEventHandler
    paymentChannelManager.connectionOpen()
  }

  def message(from: Identifier, to: Identifier, protocol: Long, message: Array[Byte], expiration: Long) = {
    try {
      socketProtobufHandler.setWriteTarget(new XicityWriteTarget(from, protocol))
    } catch {
      case _: Throwable => //pass
    }
//    println(s"||||||||||||||||||| ${message.toList}")
    socketProtobufHandler.receiveBytes(ByteBuffer.wrap(message))
  }
}
