package com.machinomy.microdemo

import com.github.nscala_time.time.Imports._
import com.machinomy.xicity.Identifier

object Peer {

  type PeerHandler = (Identifier, Identifier, Array[Byte], Long) => Unit

  import com.machinomy.xicity.{Connector, Identifier, PeerNode}

  private[this] lazy val identifier = new Identifier(34)

  private[this] var handler: PeerHandler = null

  private[this] lazy val peerNode = {
    import akka.actor.ActorSystem

    val system = ActorSystem()
    system.actorOf(PeerNode.props(identifier, handler))
  }

  def send(to: Identifier, message: Array[Byte]) = {
    val cmd = PeerNode.SendSingleMessageCommand(identifier, to, message, DateTime.now.getMillis / 1000 + 5)
    peerNode ! cmd
  }

  def start(rcv: PeerHandler) = {
    handler = rcv
    val seeds = Set(Connector("45.55.122.116"))
    peerNode ! PeerNode.StartClientsCommand(2, seeds)
  }
}