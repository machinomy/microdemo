package com.machinomy.microdemo.payments

import java.io.File
import scala.collection.mutable
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.{ListenableFuture, SettableFuture}
import com.google.protobuf.ByteString
import com.machinomy.microdemo.communication.{Peer, ReceivedEvent}
import com.machinomy.microdemo.payments.overrides.{XicityAddress, XicityHandlerFactory, XicityPaymentChannelServerListener, XicityServerConnectionEventHandler}
import com.machinomy.xicity.Identifier
import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.core.{Coin, Sha256Hash, WalletExtension}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.channels.PaymentChannelCloseException.CloseReason
import org.bitcoinj.protocols.channels.{PaymentChannelServerState, StoredPaymentChannelServerStates}
import org.bitcoinj.wallet.Protos.TransactionSigner

class Receiver(identifier: Identifier) {

  val protocol = math.min(scala.util.Random.nextLong(), Long.MaxValue - 128) + 128

  val network = TestNet3Params.get()

  val minimumDeposit = 100000
  val appKit = new ReceivingWallet()

  type ReceiverCallback = () => Unit

  class ReceivingWallet extends WalletAppKit(network, new File("receiver"), s"receiver_$identifier") {
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
        val tag = Option(info) match {
          case Some(value) => TransactionSigner.parseFrom(info)
          case None => TransactionSigner.newBuilder().setClassName("0").build()
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

  def start(peer: Peer) = {
    appKit.startAsync()
    appKit.awaitRunning()

    val handler = new Handler()

    val serverListener = new XicityPaymentChannelServerListener(appKit.peerGroup(), appKit.wallet(), 15, Coin.valueOf(minimumDeposit), handler)

    peer.whenConnected { (identifier: Identifier) =>
      serverListener.ready(identifier)
    }

    peer.registerListenerForProtocol(protocol, {
      case ReceivedEvent(from, to, text, expiration) =>
        serverListener.message(from, to, protocol, text, expiration)
    })
  }

}
