package com.machinomy.microdemo.electricity

import scala.collection.mutable
import akka.actor._
import com.machinomy.microdemo.communication.{Peer, ReceivedEvent}
import com.machinomy.xicity.Identifier
import com.machinomy.identity.{Chain, Link, Relation}
import com.machinomy.microdemo.payments.{Receiver, Sender}
import com.github.nscala_time.time.Imports._
import org.bitcoinj.core.Coin
import com.machinomy.consensus.{Participant, Production, Row}
import com.machinomy.consensus.state.PNCounter
import scodec.{Codec, DecodeResult}
import scodec.bits.BitVector

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object Tick

class House(meter: ActorRef, notifier: ActorRef, identifier: Identifier) extends Actor with ActorLogging {

  import context.dispatcher

  val QUANTUM = FiniteDuration(3.seconds.millis, duration.MILLISECONDS)
  val EPSILON = 50

  val channels: mutable.Set[Sender] = mutable.Set.empty[Sender]

  var neighbours: Set[Relation] = null

  var measureTick: Cancellable = null

  implicit val system = context.system

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val peer = Peer(identifier)

    peer.whenConnected { (id: Identifier) =>

      println(s"Connected with identifier $id")

      peer.registerListenerForProtocol(2, {
        case ReceivedEvent(from, to, message, expiration) =>
          Codec.decode[Row](BitVector(message)).toOption.map { f: DecodeResult[Row] => f.value } match {
            case Some(row) =>
              println(s"|||||--------------------> Received ow at ${new DateTime(row.timestamp)}: ${row.mapping.table}")
              if ((row.timestamp - currentRow.timestamp).abs > EPSILON) {
                val timestamp = (row.timestamp + currentRow.timestamp)/2

                println(s"|||||--------------------> Our schedule is lagging")
                measureTick.cancel()
                val nextTickDelta = QUANTUM.toMillis + timestamp - List(currentRow.timestamp, row.timestamp).min
                measureTick = context.system.scheduler.schedule(duration.FiniteDuration(nextTickDelta, duration.MILLISECONDS), QUANTUM, self, Tick)
                println(s"|||||--------------------> Adjusted MeasureTick")

                currentRow = Row(timestamp, currentRow.mapping.merge(row.mapping))

                //                disseminate(a)
                val bytes = Codec.encode(currentRow).toOption.get.toByteArray
                for (relation <- neighbours) {
                  Peer().send(relation.identifier, 2L, bytes)
                }
              } else {
                currentRow = currentRow.copy(mapping =  currentRow.mapping.merge(row.mapping))
              }

              println(s"|||||--------------------> Got new row at ${new DateTime(currentRow.timestamp)}: ${currentRow.mapping.table}")
            /*if (row.mapping.get(identifier) != prevLastMeasure) {
              println(s"|||||--------------------> Received row contains ${row.mapping.get(identifier)} for me, expected $lastMeasure")
              val delta = implicitly[Numeric[Production]].minus(prevLastMeasure, row.mapping.get(identifier))
              val nextMapping = row.mapping.increment(identifier, delta)
              disseminate(row.timestamp, nextMapping)
              sendingTick.cancel()
              sendingTick = context.system.scheduler.schedule(2 * QUANTUM, QUANTUM, self, Participant.SendingTick)
            }*/
            case None => log.error(s"|||||--------------------> Achtung!!!! Got none instead of row")
          }
      })

      val chain = new Chain()

      chain.ask(Identifier(345), Link("net.energy.belongs_to_grid")).onSuccess {
        case relations: Set[Relation] =>
          println(relations)
          neighbours = relations

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

                      measureTick = context.system.scheduler.schedule(FiniteDuration(0, duration.SECONDS), QUANTUM, self, Tick)
                    }
                  }
              })
          }
      }
    }
  }

  var currentMetrics: ElectricMetrics = null
  var currentRow: Row = Row(DateTime.now.getMillis, PNCounter[Identifier, Production]())

  override def receive: Receive = {

    case Messages.Start() =>
      log.info("Starting new House")
      meter ! Messages.Start()

    case msg @ Messages.NewReadings(metrics) =>
      currentMetrics = metrics
//      log.info(s"New Readings: \n\tgenerated: ${metrics.generated.formatted("%.3f")} kWh\n\tspent: ${metrics.spent.formatted("%.3f")} kWh")

//      val nextMapping = PNCounter().update(identifier, Production(0, metrics.spent - metrics.generated))
//      row.mapping.merge(nextMapping)

      notifier ! msg

    case Messages.ShutDown() =>
      log.info("House is shutting down")
      for (channel <- channels) {
        channel.close()
      }

    case Tick =>
      settle(currentRow.copy())
      currentRow = Row(DateTime.now.getMillis, PNCounter[Identifier, Production]().update(identifier, Production(currentMetrics.spent - currentMetrics.generated, 1)))

      val bytes = Codec.encode(currentRow).toOption.get.toByteArray
      for (relation <- neighbours) {
        Peer().send(relation.identifier, 2L, bytes)
      }
  }

  def settle(row: Row): Unit = {
    println(s"||||||||||> Last row at ${row.timestamp}: ${row.mapping.table}")
    println(s"Pre-Balance: ${row.mapping.value.volume}")
    val balance = -1 * row.mapping.value.volume
    val balanced = row.mapping.update(Identifier(-1), Production(balance, 40))
    println(s"================================================")
    println(s"||||||||||> Last row balanced: at ${row.timestamp}: ${balanced.table}")
  }
}


object House {

  def props(meter: ActorRef, notifier: ActorRef, identifier: Identifier) = Props(classOf[House], meter, notifier, identifier)

}
