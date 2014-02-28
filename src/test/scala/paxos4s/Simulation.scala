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

import scala.util.Random

import org.scalatest.Assertions
import org.slf4j.LoggerFactory

import com.typesafe.scalalogging.slf4j.Logger

class Simulation(size: Int, quorum: Int, optimize: Boolean, hazardous: Boolean, multiPaxos: Boolean, seed: Long) extends Assertions {

  val log = Logger(LoggerFactory.getLogger(getClass))
  val rnd = new Random(seed)
  var steps = 0 // can help to find a minimized test case
  var agreed = Set[Int]()
  val members = TestEnvironment.randomSetOf(rnd, size)
  private[this] val _membersArray = members.toArray
  def randomMemberId(): Int = _membersArray(rnd.nextInt(size))
  val leaderId =
    if (multiPaxos) Some(randomMemberId())
    else None
  val environment = new TestEnvironment[Int](members, hazardous)
  var instances: Map[Int, Instance[Int]] =
    TestEnvironment.createNodes[Int](members, quorum, optimize, environment.send, leaderId)

  def run(): Unit = {
    def clue = s"size=$size seed=$seed quorum=$quorum optimize=$optimize hazardous=$hazardous multiPaxos=$multiPaxos\n"
    withClue(clue) {
      simulate()
      verifyClusterState()
    }
  }

  private[this] def simulate(): Unit = {
    while (!isDone) {
      withClue(s"steps=$steps\n") {
        steps += 1
        processPendingPacket()
        perturbNode()
      }
    }
  }

  private[this] def isDone = environment.pendingPackets.isEmpty && areNodesReady

  private[this] def areNodesReady = instances.forall(_._2.agreedValue.isDefined)

  private[this] def processPendingPacket(): Unit = {
    for {
      (id, pax) <- environment.processPendingPacket(rnd)
      step <- instances(id).process(pax)
    } {
      consensusMustOccurOnlyOnce(id, step.outcome)
      instances += (id -> step.next)
    }
  }

  private[this] def consensusMustOccurOnlyOnce(id: Int, outcome: Option[Outcome[Int]]): Unit = outcome match {
    case Some(Agreed(_)) if !agreed.contains(id) => agreed += id
    case Some(Agreed(_)) if agreed.contains(id) =>
      throw new IllegalStateException("Consensus reached twice. id=" + id)
    case _ => Unit
  }

  private[this] def perturbNode(): Unit = {

    def crashNode(): Unit = {
      val id = randomMemberId()
      val instance = instances(id)
      log.trace(s"crash: id=$id")
      instances += (id -> Instance(instance.paxos, PaxosState.clearTransient(instance.state)))
    }

    def propose(): Unit = {
      val id = randomMemberId()
      val instance = instances(id)
      if (instance.agreedValue.isEmpty) {
        val value = rnd.nextInt
        log.trace(s"propose: id=$id value=$value")
        instance.propose(value).map(instance => {
          instances += (id -> instance)
        })
      }
    }

    environment.nodeAction(rnd).map(a => a match {
      case Propose => propose()
      case Crash => crashNode()
    })

  }

  private[this] def verifyClusterState(): Unit = {
    val states = instances.values.map(_.state).toList
    val expected = states.head.learnedVal
    states.tail.foreach(state => {
      assertResult(expected)(state.learnedVal)
    })
    assertResult(agreed)(members)
    val leaders = instances.values.map(i => i.state.leader).toList
    assertResult(1, "multiple leaders not allowed")(leaders.distinct.size)
  }

}

object Simulation {

  def exec(size: Int, quorum: Int, optimize: Boolean, hazardous: Boolean) =
    new Simulation(
      size = size,
      quorum = quorum,
      optimize = optimize,
      hazardous = hazardous,
      multiPaxos = Random.nextBoolean(),
      seed = Random.nextLong()).run()

}
