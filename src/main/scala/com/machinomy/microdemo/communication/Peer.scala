package com.machinomy.microdemo.communication

import scala.collection.mutable
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.github.nscala_time.time.Imports._
import com.machinomy.xicity.Identifier
import com.machinomy.xicity.transport._

sealed trait PeerEvent
case class ConnectedEvent() extends PeerEvent
case class ReceivedEvent(from: Identifier, to: Identifier, message: Array[Byte], expiration: Long) extends PeerEvent

trait PeerActor extends Actor with ActorLogging {
  def nodeIdentifier: Identifier

  var nodeActor: ActorRef = null
  var clientNodeActor: ActorRef = null

  override def preStart(): Unit = {
    nodeActor = context.actorOf(Node.props(nodeIdentifier, self))
    clientNodeActor = context.actorOf(ClientNode.props(Node.Wrap(nodeActor, Parameters.default)))
  }

  override def postStop(): Unit = {
    context.stop(nodeActor)
  }
}

class Peer(identifier: Identifier, system: ActorSystem) {

  private[Peer] case class InternalShot(to: Identifier, protocol: Long, message: Array[Byte])

  type PeerCallback = PeerEvent => Unit
  type ConnectCallback = Identifier => Unit

  private val listeners: mutable.Map[Long, mutable.Set[PeerCallback]] = mutable.Map.empty[Long, mutable.Set[PeerCallback]]
  private val connectListeners: mutable.Set[ConnectCallback] = mutable.Set.empty[ConnectCallback]

  private var isConnected = false

  private val actor: ActorRef = system.actorOf(Props(new PeerActor {

    override def nodeIdentifier: Identifier = identifier

    override def receive: Receive = {
      case Node.IsReady() =>
        log.info(s"NODEISREADY: $nodeIdentifier")
        isConnected = true

        for (callback <- connectListeners) {
          callback(identifier)
        }
      case m: Message.Shot =>
//        log.info(s"\n\nRECEIVEDSHOT $m\n\n")
        println(s"\n\nRECEIVED from ${m.from} over ${m.protocol} ||||||||||||||||||| ${m.text.toList}\n\n")
        for {
          (protocol, callbacks) <- listeners
          callback <- callbacks if protocol == m.protocol
        } callback(ReceivedEvent(m.from, m.to, m.text, m.expiration))

      case InternalShot(to, protocol, message) =>
//        println(s"\n\nPeer.Shot: $identifier -> $to\n\n")
        println(s"\n\nSEND to $to over $protocol ||||||||||||||||||| ${message.toList}\n\n")
        clientNodeActor ! Message.Shot(identifier, to, protocol, message, DateTime.now.getMillis / 1000 + 10.seconds.seconds)

      case x => println(s"\n\nxxxxxxxxxxx: $x\n\n")
    }
  }))

  def registerListenerForProtocol(protocol: Long, callback: PeerCallback): Unit = {
    listeners.update(protocol, listeners.getOrElse(protocol, mutable.Set.empty) + callback)
  }

  def whenConnected(fn: ConnectCallback) = {
    isConnected match {
      case true => fn(identifier)
      case false => connectListeners.add(fn)
    }
  }

  def send(to: Identifier, protocol: Long, message: Array[Byte]) = {
    Option(actor) match {
      case Some(ref) => ref ! InternalShot(to, protocol, message)
      case None => // pass
    }
  }
}

object Peer {

  private var peer: Peer = null

  def apply(identifier: Identifier)(implicit system: ActorSystem): Peer = {
    peer = Option(peer) match {
      case Some(value) => value
      case None => new Peer(identifier, system)
    }
    peer
  }

  def apply(): Peer = peer

}