package com.machinomy.xicity

import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Random

import net.tomp2p.connection.Bindings
import net.tomp2p.dht.{FutureSend, PeerBuilderDHT, PeerDHT}
import net.tomp2p.futures.{BaseFutureListener, FutureBootstrap, FutureDiscover}
import net.tomp2p.p2p.{PeerBuilder, RequestP2PConfiguration}
import net.tomp2p.peers.{Number160, PeerAddress}
import net.tomp2p.rpc.ObjectDataReply

import scala.concurrent.{Future, Promise}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable

case class Peer(client: net.tomp2p.p2p.Peer, dhtClient: PeerDHT) {

  def request(number: Number160, message: Array[Byte]): Option[Array[Byte]] = {
    val requestP2PConfiguration: RequestP2PConfiguration = new RequestP2PConfiguration(1, 10, 0)
    val futureSend = dhtClient.send(number).`object`(message).requestP2PConfiguration(requestP2PConfiguration).start()
    futureSend.awaitUninterruptibly()
    futureSend.rawDirectData2().values().headOption.map(_.asInstanceOf[Array[Byte]])
  }

  def reply(f: (PeerAddress, Array[Byte]) => AnyRef): Unit = {
    dhtClient.peer.objectDataReply(new ObjectDataReply {
      override def reply(sender: PeerAddress, request: scala.Any): AnyRef = {
        f(sender, request.asInstanceOf[Array[Byte]])
      }
    })
  }
}

object Peer {
  val PORT = 9333

  case class CanNotConnectException(msg: String = "") extends Exception
  case class CanNotSendException(msg: String = "") extends Exception

  def build(number: Number160, seeds: Seq[String] = Seq("192.168.50.4")): Future[Peer] = {
    val bindings = new Bindings().listenAny()
    val client = new PeerBuilder(number).ports(PORT).bindings(bindings).start()
    val dhtClient = new PeerBuilderDHT(client).start()
    var connected = false
    for (seed <- seeds if !connected) {
      val masterInetAddress = InetAddress.getByName(seed)
      val futureDiscover: FutureDiscover = client.discover.expectManualForwarding.inetAddress(masterInetAddress).ports(PORT).start
      futureDiscover.awaitUninterruptibly()
      val futureBootstrap: FutureBootstrap = client.bootstrap.inetAddress(masterInetAddress).ports(PORT).start
      futureBootstrap.awaitUninterruptibly()
      if (futureBootstrap.isSuccess && futureDiscover.isSuccess) {
        connected = true
      }
    }
    if (connected) {
      Future.successful(Peer(client, dhtClient))
    } else {
      Future.failed(CanNotConnectException())
    }
  }
}

import scala.concurrent.ExecutionContext.Implicits.global

object Test {

  def main() = {

    val peerNumber = new Number160(new Random)

    Peer.build(peerNumber, Seq("10.116.118.24")).onSuccess {
      case peer: Peer =>
        peer.reply { (sender, message) =>
          "Hello World".getBytes()
        }
    }

    Peer.build(new Number160(new Random), Seq("10.116.118.24")).onSuccess {
      case peer: Peer =>
        val reply = peer.request(peerNumber, "Hello Peer".getBytes).orNull

        println(reply.toString)
    }
  }

}