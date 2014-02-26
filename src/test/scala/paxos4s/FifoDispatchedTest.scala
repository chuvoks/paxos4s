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

import scala.collection.immutable.Queue
import scala.util.Random

import org.scalatest.Assertions
import org.scalatest.FunSuite

class FifoDispatchedTest extends FunSuite {

  class FIFOSimulation(size: Int, quorum: Int, optimize: Boolean, multiPaxos: Boolean, seed: Long) extends Assertions {

    val rnd = new Random(seed)
    val members = TestEnvironment.randomSetOf(rnd, size)
    def randomMemberId(): Int = members.toArray.apply(rnd.nextInt(size))
    val leaderId = if (multiPaxos) Some(randomMemberId()) else None
    var pendingPacketsFIFO = Queue[(Int, Pax[Int])]()
    val send: PaxOut[Int] => Unit =
      out => out.dest.foreach(dst => {
        pendingPacketsFIFO = pendingPacketsFIFO.enqueue((dst, out.pax))
      })
    var instances: Map[Int, Instance[Int]] =
      TestEnvironment.createNodes[Int](members, quorum, optimize, send, leaderId)

    def run(): Unit = {
      def clue = s"size=$size seed=$seed quorum=$quorum optimize=$optimize\n"
      withClue(clue) {
        simulate()
        verifyClusterState()
      }
    }

    private[this] def simulate(): Unit = {
      leaderId.orElse(Some(randomMemberId())).map(id =>
        instances += id -> instances(id).propose(rnd.nextInt()).get)
      while (!isDone) {
        processPendingPacket()
      }
    }

    private[this] def isDone = pendingPacketsFIFO.isEmpty && areNodesReady

    private[this] def areNodesReady = instances.forall(_._2.agreedValue.isDefined)

    private[this] def processPendingPacket(): Unit = {
      val ((id, pax), q) = pendingPacketsFIFO.dequeue
      def notPrepareNorPromise: Boolean =
        !pax.isInstanceOf[Prepare[Int]] && !pax.isInstanceOf[Promise[Int]]
      if (multiPaxos) assert(notPrepareNorPromise)
      pendingPacketsFIFO = q
      instances(id).process(pax).map(step =>
        instances += (id -> step.next))
    }

    private[this] def verifyClusterState(): Unit = {
      val states = instances.values.map(_.state).toList
      val expected = states.head.learnedVal
      states.tail.foreach(state => {
        assertResult(expected)(state.learnedVal)
      })
    }

  }

  test("Multi-Paxos must not use Prepare or Promise messages and must reach consensus after single propose") {
    for (i <- 1 to 1000) {
      val size = Random.nextInt(6) + 3
      val min = PaxosOp.minQuorum(size)
      val quorum = min + Random.nextInt(size - min)
      new FIFOSimulation(size, quorum,
        optimize = Random.nextBoolean(),
        multiPaxos = true,
        seed = Random.nextLong()).run()
    }
  }

  test("Basic Paxos must reach consensus after single propose") {
    for (i <- 1 to 1000) {
      val size = Random.nextInt(6) + 3
      val min = PaxosOp.minQuorum(size)
      val quorum = min + Random.nextInt(size - min)
      new FIFOSimulation(size, quorum,
        optimize = Random.nextBoolean(),
        multiPaxos = false,
        seed = Random.nextLong()).run()
    }
  }

  test("cluster of 4096, optimized multipaxos") {
    val size = 4096
    val quorum = PaxosOp.minQuorum(size)
    new FIFOSimulation(size, quorum,
      optimize = true,
      multiPaxos = true,
      seed = Random.nextLong()).run()
  }

  test("cluster of 4096, optimized basic paxos") {
    val size = 4096
    val quorum = PaxosOp.minQuorum(size)
    new FIFOSimulation(size, quorum,
      optimize = true,
      multiPaxos = false,
      seed = Random.nextLong()).run()
  }

}
