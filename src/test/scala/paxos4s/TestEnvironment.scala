/* Copyright 2014 Juha Heljoranta
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package paxos4s

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

sealed trait PacketAction
final case object Dispatch extends PacketAction
final case object Drop extends PacketAction
final case object Duplicate extends PacketAction
final case object NOP extends PacketAction

sealed trait NodeAction
final case object Crash extends NodeAction
final case object Propose extends NodeAction

class TestEnvironment[T](members: Set[Int], hazardous: Boolean) {

  // packets currently in-flight
  var pendingPackets = ArrayBuffer[(Int, Pax[T])]()

  // function used to send (queue) packets in test environment
  val send: PaxOut[T] => Unit = out => out.dest match {
    case dests if dests.isEmpty => {
      // TODO not used anymore?
      pendingPackets ++= dests.map(dest => (dest, out.pax))
    }
    case dests => {
      // TODO could be optimized as
      // pendingPackets ++= out.dest.map(dest => (dest, out.pax))
      // but would require some safety checks
      pendingPackets ++= dests.withFilter(members.contains).map(dest => (dest, out.pax))
    }
  }

  private[this] def packetAction(r: Random): PacketAction = r.nextInt(10 + pendingPackets.size / 2) match {
    case 0 if hazardous && pendingPackets.nonEmpty => Drop
    case 1 if hazardous && pendingPackets.nonEmpty => Duplicate
    case _ if pendingPackets.nonEmpty => Dispatch
    case _ => NOP
  }

  def nodeAction(r: Random): Option[NodeAction] = r.nextInt(20 + pendingPackets.size / 2) match {
    case _ if pendingPackets.isEmpty => Some(Propose)
    case 0 if hazardous => Some(Crash)
    case 1 if hazardous => Some(Crash)
    case 2 if hazardous => Some(Propose)
    case 3 => Some(Propose) // always possibility of competing proposals
    case _ => None
  }

  def processPendingPacket(r: Random): Option[(Int, Pax[T])] = packetAction(r) match {
    case Drop => {
      pendingPackets.remove(r.nextInt(pendingPackets.size))
      None
    }
    case Duplicate => {
      pendingPackets :+= pendingPackets(r.nextInt(pendingPackets.size))
      None
    }
    case Dispatch => {
      // message dispatch happens always in random order
      Some(pendingPackets.remove(r.nextInt(pendingPackets.size)))
    }
    case NOP => None
  }

}

object TestEnvironment {

  def createNodes[T](
    members: Set[Int],
    quorum: Int,
    optimize: Boolean,
    send: PaxOut[T] => Unit): Map[Int, Instance[T]] = {
    val nopPersist: PaxosState[T] => Unit = a => Unit
    var nodes = Map[Int, Instance[T]]()
    for (id <- members) {
      val p = PaxosOp[T](id, quorum, members - id, send, nopPersist, optimize)
      val s = PaxosState.empty[T]
      nodes += id -> Instance(p, s)
    }
    nodes
  }

}
