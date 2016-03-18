package one.eliot.microdemo

import java.io.File
import java.net.SocketAddress

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.{SettableFuture, ListenableFuture}
import com.google.protobuf.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.bitcoin.paymentchannel.Protos.{Settlement, PaymentAck}
import org.bitcoinj.core.{Sha256Hash, WalletExtension, Coin, Utils}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.channels.PaymentChannelCloseException.CloseReason
import org.bitcoinj.protocols.channels.PaymentChannelServerListener.HandlerFactory
import org.bitcoinj.protocols.channels.{PaymentChannelServerState, ServerConnectionEventHandler, PaymentChannelServerListener, StoredPaymentChannelServerStates}
import org.bitcoinj.wallet.Protos.{TransactionSigner, Tag}

import scala.util.Random

object Receiver extends App {
  val network = TestNet3Params.get()

  class ReceivingWallet extends WalletAppKit(network, new File("receiver"), "receiver") {
    override def provideWalletExtensions = {
      ImmutableList.of[WalletExtension](new StoredPaymentChannelServerStates(null))
    }
  }

  class Handler extends HandlerFactory {
    override def onNewConnection(client: SocketAddress) = new ServerConnectionEventHandler with LazyLogging {
      override def channelOpen(channelId: Sha256Hash): Unit = {
        logger.info(s"Channel open for $client: id# $channelId")
        val wallet = appKit.wallet()
        val storedStates: StoredPaymentChannelServerStates =
          wallet.getExtensions.get(classOf[StoredPaymentChannelServerStates].getName).asInstanceOf[StoredPaymentChannelServerStates]
        val state: PaymentChannelServerState = storedStates.getChannel(channelId).getOrCreateState(wallet, appKit.peerGroup())
        val maximumValue = state.getMultisigContract.getOutput(0).getValue
        val expiration = state.getRefundTransactionUnlockTime + StoredPaymentChannelServerStates.CHANNEL_EXPIRE_OFFSET
        logger.info(s"   with a maximum value of ${maximumValue.toFriendlyString}, expiring at UNIX timestamp $expiration.")
      }

      override def paymentIncrease(by: Coin, to: Coin, info: ByteString): ListenableFuture[ByteString] = {
        logger.info(s"Client $client paid increased payment by $by for a total of $to")
        val tag = if (info != null) {
          TransactionSigner.parseFrom(info)
        } else {
          TransactionSigner.newBuilder().setClassName("0").build()
        }
        val requestedValue = tag.getClassName.toInt
        val result = requestedValue * 100000
        logger.info(s"Requested calculation for $requestedValue")
        logger.info(s"The calculation is $result")
        val future: SettableFuture[ByteString] = SettableFuture.create[ByteString]()
        future.set(TransactionSigner.newBuilder().setClassName(result.toString).build.toByteString)
        future
      }

      override def channelClosed(reason: CloseReason): Unit = {
        logger.info(s"Client $client closed channel: $reason")
      }
    }
  }

  val minimumDeposit = 100000
  val appKit = new ReceivingWallet()
  appKit.startAsync()
  appKit.awaitRunning()

  val handler = new Handler()

  new PaymentChannelServerListener(appKit.peerGroup(), appKit.wallet(), 15, Coin.valueOf(minimumDeposit), handler).bindAndStart(8484)
}
