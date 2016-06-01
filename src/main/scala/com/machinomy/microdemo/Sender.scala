package com.machinomy.microdemo

import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.locks.{ReentrantLock, ReadWriteLock}
import java.util.concurrent.{Executors, CountDownLatch}

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent._
import com.google.protobuf.ByteString
import com.machinomy.xicity.{Connector, Identifier}
import com.typesafe.scalalogging.LazyLogging
import org.bitcoin.paymentchannel.Protos
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.ProtobufParser
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.core._
import org.bitcoinj.protocols.channels._
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.Protos.TransactionSigner
import org.spongycastle.crypto.params.KeyParameter

import scala.concurrent.duration._
import scala.concurrent._


object Sender extends App with LazyLogging {
  val serverHost = "localhost"
  val serverPort = 8484

  val channelSize = Coin.MILLICOIN.multiply(5).multiply(10)
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

  val serverSocketAddress = new XicityAddress(new Identifier(128))
  val channelId = serverHost
  logger.info("1:...")
  openAndSend(timeout, serverSocketAddress, channelId, 10)

  def waitForRefill(amount: Coin): Unit = {
    val amountPlusFee = amount.add(Wallet.SendRequest.DEFAULT_FEE_PER_KB)
    val balanceFuture = appKit.wallet().getBalanceFuture(amountPlusFee, Wallet.BalanceType.ESTIMATED)
    if (!balanceFuture.isDone) {
      println(s"Send at least ${amountPlusFee.toFriendlyString} to ${myKey.toAddress(network)}")
      Futures.getUnchecked(balanceFuture)
    }
  }

//  var channelClient: PaymentChannelClient = null

//  val latch = new CountDownLatch(1)



  def openAndSend(timeout: Int, server: XicityAddress, channelId: String, times: Int): Unit = {

    def sendTransactions(): Unit = {
      println(channelClient, channelClient.state())
      val alreadySpent = channelClient.state().getValueSpent
      logger.info(s"Connected. Trying to make 1 micropayment. Already paid $alreadySpent on the channel")
      val quantumPayment = Coin.MILLICOIN.multiply(5)
//      for (i <- 1 to 5) {
        val request = TransactionSigner.newBuilder.setClassName("1").build()
//        val lock = new ReentrantLock()
//        lock.lock()
        var listenableFuture: ListenableFuture[PaymentIncrementAck] = null
//        val incrementThread = new Thread(new Runnable {
//          override def run(): Unit = {
        listenableFuture = channelClient.incrementPayment(quantumPayment, request.toByteString, null)
//            lock.unlock()
//          }
//        })
//
//        lock.lock()
//
//        // val listenableFuture: ListenableFuture[PaymentIncrementAck] = Await.result[ListenableFuture[PaymentIncrementAck]](f, 10 minutes)
//        lock.unlock()
//
//        val incrementAck: PaymentIncrementAck = Uninterruptibles.getUninterruptibly(listenableFuture)
      listenableFuture.addListener(new Runnable {
        override def run(): Unit = {
          val incrementAck: PaymentIncrementAck = listenableFuture.get()

          val response: TransactionSigner = TransactionSigner.parseFrom(incrementAck.getInfo)
          val gotValue = response.getClassName.toInt
          logger.info(s"Sucessfully received $gotValue calculated on base of 1")
          logger.info(s"Sucessfully sent $quantumPayment, ${channelClient.state().getValueRefunded} remains on channel")
          //      }
          if (channelClient.state().getValueRefunded.compareTo(channelSize) < 0) {
            logger.info("Settling the channel")
            channelClient.settle()
          }
        }
      }, MoreExecutors.sameThreadExecutor)
      //    latch.countDown()
    }

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

        sendTransactions()
      }
    })

    // And glue back in the opposite direction - network to the channelClient.
    lazy val wireParser: ProtobufParser[Protos.TwoWayChannelMessage] = new ProtobufParser[Protos.TwoWayChannelMessage](new ProtobufParser.Listener[Protos.TwoWayChannelMessage]() {
      def messageReceived(handler: ProtobufParser[Protos.TwoWayChannelMessage], msg: Protos.TwoWayChannelMessage) {
        try {
          channelClient.receiveMessage(msg)
        }
        catch {
          case e: Throwable => println(s"ERROR: ${e.toString}")
//          case e: InsufficientMoneyException =>
            //            channelOpenFuture.setException(e)
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


    Peer.identifier = new Identifier(34)
    println("PEER START ...................")
    Peer.start({
      case Peer.ConnectedEvent() =>
        try {
          wireParser.setWriteTarget(new XicityWriteTarget(new Identifier(128)))
        } catch {
          case _: Throwable => //pass
        }
        wireParser.connectionOpened()
        println("CONNECTED ~~~~~~~~~~")


      case Peer.ReceivedEvent(from, to, message, expiration) =>
        wireParser.receiveBytes(ByteBuffer.wrap(message))
    })


//        val clientConnection = new PaymentChannelClientConnection(server, timeout, appKit.wallet(), myKey, channelSize, channelId)

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


//    latch.await()
  }
}
