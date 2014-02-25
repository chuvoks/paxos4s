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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.util.Success

import org.scalatest.FunSuite

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala

class TestActor[T](var instance: Instance[T], exitLatch: CountDownLatch) extends Actor {

  def receive = {
    case pax: Pax[T] => instance.process(pax) match {
      case Success(Step(next, Some(Agreed(t)))) => {
        exitLatch.countDown()
        context.stop(self)
      }
      case Success(Step(next, _)) => instance = next
      case a => throw new IllegalStateException("Cannot handle: " + a)
    }
    case s: T @unchecked => instance.propose(s.asInstanceOf[T]) match {
      case Success(next) => instance = next
      case a => throw new IllegalStateException("Cannot handle: " + a)
    }
  }

}

class ConsensusDemo[T](val system: ActorSystem) {

  private[this] val members: Set[Int] = (1 to 1000).toSet

  private[this] val exitLatch: CountDownLatch = new CountDownLatch(members.size)

  private[this] val actors: Map[Int, ActorRef] = members.map(id => {
    val instance = createInstance[T](id, send(_))
    (id -> system.actorOf(Props(new TestActor[T](instance, exitLatch))))
  }).toMap

  private[this] def send(out: PaxOut[T]): Unit =
    out.dest.foreach(dst => actors(dst) ! out.pax)

  private[this] def createInstance[T](id: Int, send: PaxOut[T] => Unit): Instance[T] = {
    val nopPersist: PaxosState[T] => Unit = a => Unit
    Instance.empty(id, members, nopPersist, send)
  }

  /** return true if success. false on timeout.*/
  def run(t: T): Boolean = {
    actors(1) ! t
    exitLatch.await(6, TimeUnit.SECONDS)
  }

}

class AkkaConsensusTest extends FunSuite {

  test("Akka consensus demo") {
    val system = ActorSystem.create()
    try {
      assert(new ConsensusDemo[String](system).run("woot"))
    } finally {
      system.shutdown()
    }
  }

}
