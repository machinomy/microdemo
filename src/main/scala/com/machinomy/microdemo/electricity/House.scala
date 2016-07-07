package com.machinomy.microdemo.electricity

import scala.collection.mutable
import akka.actor._
import com.machinomy.microdemo.communication.{Peer, ReceivedEvent}
import com.machinomy.xicity.Identifier
import com.machinomy.identity.{Chain, Link, Relation}
import com.machinomy.microdemo.payments.{Receiver, Sender}
import com.github.nscala_time.time.Imports._
import org.bitcoinj.core.Coin
import com.machinomy.consensus.{Production, Row}
import com.machinomy.consensus.state.PNCounter

import scala.concurrent.duration.FiniteDuration

class House(meter: ActorRef, notifier: ActorRef, identifier: Identifier) extends Actor with ActorLogging {

  val channels: mutable.Set[Sender] = mutable.Set.empty[Sender]

  val row = new Row(DateTime.now.getMillis, PNCounter())

  implicit val system = context.system

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val peer = Peer(identifier)

    peer.whenConnected { (id: Identifier) =>

      println(s"Connected with identifier $id")

      val chain = new Chain()

      import scala.concurrent.ExecutionContext.Implicits.global

      chain.ask(Identifier(345), Link("net.energy.belongs_to_grid")).onSuccess {
        case relations: Set[Relation] =>
          println(relations)

          val receiver = new Receiver(identifier)
          receiver.start(peer)

          peer.registerListenerForProtocol(128, {
            case ReceivedEvent(from, to, message, expiration) =>
              val strMessage = message.map(_.toChar).mkString
              strMessage match {
                case "Sender" =>
                  peer.send(from, 128, s"Receiver${receiver.protocol}".getBytes)
                case _ => //pass
              }
          })

          for (relation <- relations if relation.identifier != identifier) {
            val sender = new Sender(relation.identifier)
            channels.add(sender)
              val cancellable: Cancellable = context.system.scheduler.schedule(FiniteDuration(0, "second"), FiniteDuration(30.seconds.millis, "ms")) {
                peer.send(relation.identifier, 128, "Sender".getBytes)
              }

              peer.registerListenerForProtocol(128, {
                case ReceivedEvent(from, to, message, expiration) =>
                  val strMessage = message.map(_.toChar).mkString
                  if (strMessage.startsWith("Receiver")) {
                    val protocol = strMessage.replaceAll("Receiver", "").toLong
                    cancellable.cancel()
                    sender.open(peer, protocol)

                    sender.whenOpened { () =>
//                      sender.send(Coin.MILLICOIN)
                      val nextMapping = PNCounter().update(identifier, Production(0, 1))
                      row.mapping.merge(nextMapping)
                    }
                  }
              })
          }
      }
    }
  }

  override def receive: Receive = {

    case Messages.Start() =>
      log.info("Starting new House")
      meter ! Messages.Start()

    case msg @ Messages.NewReadings(metrics) =>
      log.info(s"New Readings: \n\tgenerated: ${metrics.generated.formatted("%.3f")} kWh\n\tspent: ${metrics.spent.formatted("%.3f")} kWh")

      val nextMapping = PNCounter().update(identifier, Production(0, metrics.spent - metrics.generated))
      row.mapping.merge(nextMapping)

      notifier ! msg

    case Messages.ShutDown() =>
      log.info("House is shutting down")
      for (channel <- channels) {
        channel.close()
      }
  }
}


object House {

  def props(meter: ActorRef, notifier: ActorRef, identifier: Identifier) = Props(classOf[House], meter, notifier, identifier)

}