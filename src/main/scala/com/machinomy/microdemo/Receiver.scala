package com.machinomy.microdemo

import java.io.File
import java.net.SocketAddress
import java.nio.ByteBuffer

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.{ListenableFuture, SettableFuture}
import com.google.protobuf.ByteString
import com.machinomy.xicity.Identifier
import com.typesafe.scalalogging.LazyLogging
import org.bitcoin.paymentchannel.Protos
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.{MessageWriteTarget, ProtobufParser}
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.channels.PaymentChannelCloseException.CloseReason
import org.bitcoinj.protocols.channels._
import org.bitcoinj.wallet.Protos.TransactionSigner

case class XicityAddress(identifier: Identifier) extends SocketAddress {

  def getBytes: Array[Byte] = {
    identifier.toString.getBytes()
  }
}

trait XicityHandlerFactory {
  def onNewConnection (clientAddress: XicityAddress): XicityServerConnectionEventHandler
}

abstract class XicityServerConnectionEventHandler extends ServerConnectionEventHandler

class XicityWriteTarget(identifier: Identifier) extends MessageWriteTarget {
  override def writeBytes(message: Array[Byte]): Unit = {
    Peer.send(identifier, message)
  }

  override def closeConnection(): Unit = {
    // pass
  }
}

class XicityPaymentChannelServerListener(broadcaster: TransactionBroadcaster, wallet: Wallet, timeout: Int, minAcceptedChannelSize: Coin, eventHandlerFactory: XicityHandlerFactory) {

  val socketProtobufHandler = new ProtobufParser[Protos.TwoWayChannelMessage](protobufHandlerListener, Protos.TwoWayChannelMessage.getDefaultInstance, Short.MaxValue, 15 * 1000)

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
      paymentChannelManager.receiveMessage(msg)
    }

    def connectionClosed(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
      paymentChannelManager.connectionClosed()
      if (closeReason != null) eventHandler.channelClosed(closeReason)
      else eventHandler.channelClosed(PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED)
    }

    def connectionOpen(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
      val newEventHandler: XicityServerConnectionEventHandler = eventHandlerFactory.onNewConnection(new XicityAddress(new Identifier(128)))
      if (newEventHandler == null) handler.closeConnection()
      else {
        eventHandler = newEventHandler
        paymentChannelManager.connectionOpen()
      }
    }
  }

  private var closeReason: PaymentChannelCloseException.CloseReason = null
  private var eventHandler: XicityServerConnectionEventHandler = null

  def bindAndStart() = {
//    Peer.identifier = new Identifier(128)
//    Peer.start({
//      case Peer.ConnectedEvent() =>
//        println("CONNECTED ~~~~~~~~~~")
//        val newEventHandler: XicityServerConnectionEventHandler = eventHandlerFactory.onNewConnection(new XicityAddress(new Identifier(128)))
//        eventHandler = newEventHandler
//        paymentChannelManager.connectionOpen()
//      case Peer.ReceivedEvent(from, to, message, expiration) =>
//        try {
//          socketProtobufHandler.setWriteTarget(new XicityWriteTarget(from))
//        } catch {
//          case _: Throwable => //pass
//        }
//        println(s"||||||||||||||||||| ${message.map("%02x".format(_)).mkString("")}")
//        socketProtobufHandler.receiveBytes(ByteBuffer.wrap(message))
//    })
  }
}

object Receiver extends App {
  val network = TestNet3Params.get()
  class ReceivingWallet extends WalletAppKit(network, new File("receiver"), "receiver") {
    override def provideWalletExtensions = {
      ImmutableList.of[WalletExtension](new StoredPaymentChannelServerStates(null))
    }
  }

  class Handler extends XicityHandlerFactory {
    override def onNewConnection(client: XicityAddress) = new XicityServerConnectionEventHandler with LazyLogging {

      override def channelOpen(channelId: Sha256Hash): Unit = {
        println(s"Channel open for $client: id# $channelId")
        val wallet = appKit.wallet()
        val storedStates: StoredPaymentChannelServerStates =
          wallet.getExtensions.get(classOf[StoredPaymentChannelServerStates].getName).asInstanceOf[StoredPaymentChannelServerStates]
        val state: PaymentChannelServerState = storedStates.getChannel(channelId).getOrCreateState(wallet, appKit.peerGroup())
        val maximumValue = state.getMultisigContract.getOutput(0).getValue
        val expiration = state.getRefundTransactionUnlockTime + StoredPaymentChannelServerStates.CHANNEL_EXPIRE_OFFSET
        println(s"   with a maximum value of ${maximumValue.toFriendlyString}, expiring at UNIX timestamp $expiration.")
      }

      override def paymentIncrease(by: Coin, to: Coin, info: ByteString): ListenableFuture[ByteString] = {
        println(s"Client $client paid increased payment by $by for a total of $to")
        val tag = if (info != null) {
          TransactionSigner.parseFrom(info)
        } else {
          TransactionSigner.newBuilder().setClassName("0").build()
        }
        val requestedValue = tag.getClassName.toInt
        val result = requestedValue * 100000
        println(s"Requested calculation for $requestedValue")
        println(s"The calculation is $result")
        val future: SettableFuture[ByteString] = SettableFuture.create[ByteString]()
        future.set(TransactionSigner.newBuilder().setClassName(result.toString).build.toByteString)
        future
      }

      override def channelClosed(reason: CloseReason): Unit = {
        println(s"Client $client closed channel: $reason")
      }
    }
  }

  val minimumDeposit = 100000
  val appKit = new ReceivingWallet()
  appKit.startAsync()
  appKit.awaitRunning()

  val handler = new Handler()

  new XicityPaymentChannelServerListener(appKit.peerGroup(), appKit.wallet(), 15, Coin.valueOf(minimumDeposit), handler).bindAndStart()
}
