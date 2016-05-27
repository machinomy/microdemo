package com.machinomy.microdemo

import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.{ListenableFuture, Uninterruptibles, FutureCallback, Futures}
import com.machinomy.xicity.Identifier
import com.typesafe.scalalogging.LazyLogging
import org.bitcoin.paymentchannel.Protos
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.ProtobufParser
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.core.Coin
import org.bitcoinj.core._
import org.bitcoinj.protocols.channels._
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.Protos.TransactionSigner

object Sender extends App with LazyLogging {
  val serverHost = "localhost"
  val serverPort = 8484

  val channelSize = Coin.COIN.divide(10)
  val myKey = new ECKey()
  val network = TestNet3Params.get()
  val timeout = 15

  class SendingWallet extends WalletAppKit(network, new File("sender"), "sender") {
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

  val serverSocketAddress = new XicityAddress(new Identifier(100))
  val channelId = serverHost
  logger.info("1:...")
//  openAndSend(timeout, serverSocketAddress, channelId, 10)

  def waitForRefill(amount: Coin): Unit = {
    val amountPlusFee = amount.add(Wallet.SendRequest.DEFAULT_FEE_PER_KB)
    val balanceFuture = appKit.wallet().getBalanceFuture(amountPlusFee, Wallet.BalanceType.ESTIMATED)
    if (!balanceFuture.isDone) {
      println(s"Send at least ${amountPlusFee.toFriendlyString} to ${myKey.toAddress(network)}")
      Futures.getUnchecked(balanceFuture)
    }
  }

  def openAndSend(timeout: Int, server: XicityAddress, channelId: String, times: Int): Unit = {
    lazy val channelClient = new PaymentChannelClient(appKit.wallet(), myKey, channelSize, Sha256Hash.of(server.getBytes), PaymentChannelClient.DEFAULT_TIME_WINDOW, null, new IPaymentChannelClient.ClientConnection() {
      def sendToServer(msg: Protos.TwoWayChannelMessage) {
        wireParser.write(msg)
      }

      def destroyConnection(reason: PaymentChannelCloseException.CloseReason) {
//        channelOpenFuture.setException(new PaymentChannelCloseException("Payment channel client requested that the connection be closed: " + reason, reason))
        wireParser.closeConnection()
      }

      def acceptExpireTime(expireTime: Long): Boolean = {
        expireTime <= (PaymentChannelClient.DEFAULT_TIME_WINDOW + Utils.currentTimeSeconds + 60)
      }

      def channelOpen(wasInitiated: Boolean) {
        wireParser.setSocketTimeout(0)
//        channelOpenFuture.set(PaymentChannelClientConnection.this)
      }
    })

    // And glue back in the opposite direction - network to the channelClient.
    lazy val wireParser: ProtobufParser[Protos.TwoWayChannelMessage] = new ProtobufParser[Protos.TwoWayChannelMessage](new ProtobufParser.Listener[Protos.TwoWayChannelMessage]() {
      def messageReceived(handler: ProtobufParser[Protos.TwoWayChannelMessage], msg: Protos.TwoWayChannelMessage) {
        try {
          channelClient.receiveMessage(msg)
        }
        catch {
          case e: InsufficientMoneyException => {
//            channelOpenFuture.setException(e)
          }
        }
      }

      def connectionOpen(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
        channelClient.connectionOpen()
      }

      def connectionClosed(handler: ProtobufParser[Protos.TwoWayChannelMessage]) {
        channelClient.connectionClosed()
//        channelOpenFuture.setException(new PaymentChannelCloseException("The TCP socket died", PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED))
      }
    }, Protos.TwoWayChannelMessage.getDefaultInstance, Short.MaxValue, 15 * 1000)

    wireParser.setWriteTarget(new XicityWriteTarget(new Identifier(100)))
    wireParser.connectionOpened()

//    val clientConnection = new PaymentChannelClientConnection(server, timeout, appKit.wallet(), myKey, channelSize, channelId)
//    val latch = new CountDownLatch(1)
//    Futures.addCallback(clientConnection.getChannelOpenFuture, new FutureCallback[PaymentChannelClientConnection] {
//      override def onFailure(t: Throwable): Unit = {
//        logger.error("Failed to open connection", t)
//        latch.countDown()
//      }
//      override def onSuccess(client: PaymentChannelClientConnection): Unit = {
//        val alreadySpent = client.state().getValueSpent
//        logger.info(s"Connected. Trying to make $times micropayments. Already paid $alreadySpent on the channel")
//        val quantumPayment = Coin.MILLICOIN
//        for (i <- 1 to times) {
//          val request = TransactionSigner.newBuilder.setClassName(i.toString).build()
//          val listenableFuture: ListenableFuture[PaymentIncrementAck] = client.incrementPayment(quantumPayment, request.toByteString, null)
//          val incrementAck: PaymentIncrementAck = Uninterruptibles.getUninterruptibly(listenableFuture)
//          val response: TransactionSigner = TransactionSigner.parseFrom(incrementAck.getInfo)
//          val gotValue = response.getClassName.toInt
//          logger.info(s"Sucessfully received $gotValue calculated on base of $i")
//          logger.info(s"Sucessfully sent $quantumPayment, ${client.state().getValueRefunded} remains on channel")
//        }
//        if (client.state().getValueRefunded.compareTo(channelSize) < 0) {
//          logger.info("Settling the channel")
//          client.settle()
//        }
//        latch.countDown()
//      }
//    }, Threading.USER_THREAD)

    val alreadySpent = channelClient.state().getValueSpent
    logger.info(s"Connected. Trying to make $times micropayments. Already paid $alreadySpent on the channel")
    val quantumPayment = Coin.MILLICOIN
    for (i <- 1 to times) {
      val request = TransactionSigner.newBuilder.setClassName(i.toString).build()
      val listenableFuture: ListenableFuture[PaymentIncrementAck] = channelClient.incrementPayment(quantumPayment, request.toByteString, null)
      val incrementAck: PaymentIncrementAck = Uninterruptibles.getUninterruptibly(listenableFuture)
      val response: TransactionSigner = TransactionSigner.parseFrom(incrementAck.getInfo)
      val gotValue = response.getClassName.toInt
      logger.info(s"Sucessfully received $gotValue calculated on base of $i")
      logger.info(s"Sucessfully sent $quantumPayment, ${channelClient.state().getValueRefunded} remains on channel")
    }
    if (channelClient.state().getValueRefunded.compareTo(channelSize) < 0) {
      logger.info("Settling the channel")
      channelClient.settle()
    }
//    latch.countDown()

    Peer.start {(from, to, message, expiration) => wireParser.receiveBytes(ByteBuffer.wrap(message))}

//    latch.await()
  }
}
