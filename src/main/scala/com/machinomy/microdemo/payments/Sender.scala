package com.machinomy.microdemo.payments

import java.io.File
import java.nio.ByteBuffer
import scala.collection.mutable
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.{Futures, ListenableFuture, MoreExecutors}
import com.machinomy.microdemo.communication.{Peer, ReceivedEvent}
import com.machinomy.microdemo.payments.overrides.{XicityAddress, XicityWriteTarget}
import com.machinomy.xicity.Identifier
import com.typesafe.scalalogging.LazyLogging
import org.bitcoin.paymentchannel.Protos
import org.bitcoinj.core.{Coin, ECKey, Sha256Hash, Utils, Wallet, WalletExtension}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.ProtobufParser
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.channels._
import org.bitcoinj.wallet.Protos.TransactionSigner

class Sender(identifier: Identifier, serverIdentifier: Identifier) extends LazyLogging {
  val channelSize = Coin.MILLICOIN.multiply(5).multiply(10)
  val myKey = new ECKey()
  val network = TestNet3Params.get()
  val timeout = 15


  type OpenChannelCallback = () => Unit
  val listeners: mutable.Set[OpenChannelCallback] = mutable.Set.empty[OpenChannelCallback]

  class SendingWallet extends WalletAppKit(network, new File("sender"), s"sender_${identifier.n}_${serverIdentifier.n}") {
    override def provideWalletExtensions() = {
      ImmutableList.of[WalletExtension](new StoredPaymentChannelClientStates(null))
    }
  }

  val appKit = new SendingWallet()
  appKit.startAsync()
  appKit.awaitRunning()
  appKit.wallet().importKey(myKey)
  appKit.wallet().allowSpendingUnconfirmedTransactions()

  println(appKit.wallet())
  waitForRefill(channelSize)

  val serverSocketAddress = new XicityAddress(serverIdentifier)
  logger.info("1:...")

  def waitForRefill(amount: Coin): Unit = {
    val amountPlusFee = amount.add(Wallet.SendRequest.DEFAULT_FEE_PER_KB)
    val balanceFuture = appKit.wallet().getBalanceFuture(amountPlusFee, Wallet.BalanceType.ESTIMATED)
    if (!balanceFuture.isDone) {
      println(s"Send at least ${amountPlusFee.toFriendlyString} to ${myKey.toAddress(network)}")
      Futures.getUnchecked(balanceFuture)
    }
  }

  var channelClient: PaymentChannelClient = null

  def send(amount: Coin): Unit = {
    println(channelClient, channelClient.state())
    val alreadySpent = channelClient.state().getValueSpent
    logger.info(s"Connected. Trying to make 1 micropayment. Already paid $alreadySpent on the channel")
//    val quantumPayment = Coin.MILLICOIN.multiply(5)

    val request = TransactionSigner.newBuilder.setClassName("1").build()

    var listenableFuture: ListenableFuture[PaymentIncrementAck] = null

    listenableFuture = channelClient.incrementPayment(amount, request.toByteString, null)

    listenableFuture.addListener(new Runnable {
      override def run(): Unit = {
        val incrementAck: PaymentIncrementAck = listenableFuture.get()

        val response: TransactionSigner = TransactionSigner.parseFrom(incrementAck.getInfo)
        val gotValue = response.getClassName.toInt
        logger.info(s"Sucessfully received $gotValue calculated on base of 1")
        logger.info(s"Sucessfully sent $amount, ${channelClient.state().getValueRefunded} remains on the channel")
//        if (channelClient.state().getValueRefunded.compareTo(channelSize) < 0) {
//          logger.info("Settling the channel")
//          channelClient.settle()
//        }
      }
    }, MoreExecutors.sameThreadExecutor)
  }

  def close(): Unit = {
    if (channelClient != null)
      channelClient.settle()
  }

  def open(peer: Peer, protocol: Long): Unit = {

    //lazy val
    channelClient = new PaymentChannelClient(appKit.wallet(), myKey, channelSize, Sha256Hash.of(serverSocketAddress.getBytes), PaymentChannelClient.DEFAULT_TIME_WINDOW, null, new IPaymentChannelClient.ClientConnection() {
      def sendToServer(msg: Protos.TwoWayChannelMessage) {
        wireParser.write(msg)
      }

      def destroyConnection(reason: PaymentChannelCloseException.CloseReason) {
        wireParser.closeConnection()
      }

      def acceptExpireTime(expireTime: Long): Boolean = {
        expireTime <= (PaymentChannelClient.DEFAULT_TIME_WINDOW + Utils.currentTimeSeconds + 60)
      }

      def channelOpen(wasInitiated: Boolean) {
        wireParser.setSocketTimeout(0)
        for (listener <- listeners) {
          listener()
        }
//        sendTransactions()
      }
    })

    lazy val wireParser: ProtobufParser[Protos.TwoWayChannelMessage] = new ProtobufParser[Protos.TwoWayChannelMessage](new ProtobufParser.Listener[Protos.TwoWayChannelMessage]() {
      def messageReceived(handler: ProtobufParser[Protos.TwoWayChannelMessage], msg: Protos.TwoWayChannelMessage) {
        try {
          channelClient.receiveMessage(msg)
        }
        catch {
          case e: Throwable => println(s"ERROR: ${e.toString}")
        }
      }

      def connectionOpen(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
        channelClient.connectionOpen()
      }

      def connectionClosed(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
        channelClient.connectionClosed()
      }
    }, Protos.TwoWayChannelMessage.getDefaultInstance, Short.MaxValue, 15 * 1000)


    //    Peer.identifier = new Identifier(34)
    println("PEER START .....................")

    peer.whenConnected { (identifier: Identifier) =>
      try {
        wireParser.setWriteTarget(new XicityWriteTarget(serverIdentifier, protocol))
      } catch {
        case _: Throwable => //pass
      }
      wireParser.connectionOpened()
    }

    // TODO: hardcode
    peer.registerListenerForProtocol(protocol, {
      case ReceivedEvent(from, to, message, expiration) =>
        wireParser.receiveBytes(ByteBuffer.wrap(message))
    })

    //    Peer.start({
    //      case Peer.ConnectedEvent() =>
    //        try {
    //          wireParser.setWriteTarget(new XicityWriteTarget(new Identifier(128)))
    //        } catch {
    //          case _: Throwable => //pass
    //        }
    //        wireParser.connectionOpened()
    //        println("CONNECTED ~~~~~~~~~~")
    //
    //
    //      case Peer.ReceivedEvent(from, to, message, expiration) =>
    //        wireParser.receiveBytes(ByteBuffer.wrap(message))
    //    })
  }

  def whenOpened(fn: OpenChannelCallback): Unit = {
    listeners.add(fn)
  }

  def isOpened: Boolean = {
    channelClient != null && channelClient.state() != null
  }
}