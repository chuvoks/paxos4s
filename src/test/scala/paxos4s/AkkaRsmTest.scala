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

import scala.collection.immutable.TreeMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.{ Promise => ScalaPromise }
import scala.concurrent.duration.Duration
import scala.util.Success

import org.scalatest.FunSuite

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala

case class RsmPax[T](stateId: Long, pax: Pax[T])

case class Get[T](promise: ScalaPromise[Map[Long, Option[T]]])

class RSM[T](createInstance: Long => Instance[T]) extends Actor {

  private[this] var stateMap: TreeMap[Long, Instance[T]] = TreeMap()

  def receive = {
    case rsmPax: RsmPax[T] => rsmPax match {
      case RsmPax(stateId, pax) => {
        val instance = stateMap.getOrElse(stateId, createInstance(stateId))
        instance.process(pax) match {
          case Success(Step(next, Some(Agreed(t)))) => {
            stateMap += (stateId -> next)
          }
          case Success(Step(next, _)) => stateMap += (stateId -> next)
          case a => throw new IllegalStateException("Cannot handle: " + a)
        }
      }
    }
    case g: Get[T] =>
      g.promise.complete(Success(stateMap.mapValues(_.agreedValue)))
    case t: T @unchecked => {
      val newStateId = stateMap.lastOption.map(_._1).getOrElse(0L) + 1L
      createInstance(newStateId).propose(t.asInstanceOf[T]) match {
        case Success(next) => stateMap += (newStateId -> next)
        case a => throw new IllegalStateException("Cannot handle: " + a)
      }
    }
  }

}

class RMSDemo[T](val system: ActorSystem) {

  private[this] val members: Set[Int] = (1 to 10).toSet

  private[this] val actors: Map[Int, ActorRef] = members.map(id => {
    val instanceFun: Long => Instance[T] = stateId => createInstance[T](id, send(stateId, _))
    (id -> system.actorOf(Props(new RSM[T](instanceFun))))
  }).toMap

  private[this] def send(stateId: Long, out: PaxOut[T]): Unit =
    out.dest.foreach(dst => actors(dst) ! RsmPax(stateId, out.pax))

  private[this] def createInstance[T](id: Int, send: PaxOut[T] => Unit): Instance[T] = {
    val nopPersist: PaxosState[T] => Unit = a => Unit
    Instance.empty(None, id, members, nopPersist, send)
  }

  private[this] def get: Future[Map[Long, Option[T]]] = {
    val getReq = Get(ScalaPromise[Map[Long, Option[T]]]())
    actors(2) ! getReq
    getReq.promise.future
  }

  def waitFor(id: Int, x: T): Unit = {
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global
    actors(id) ! x
    Await.result(Future {
      var i = 1
      while (Await.result(get, Duration("3 seconds")).filter(_._2 == Some(x)).isEmpty) {
        Thread.sleep(i)
        i += 1
        // retry, initial attempt might have just triggered the node to learn a value already agreed by others
        actors(id) ! x
      }
    }, Duration("3 seconds"))
  }

  /** throws timeout exception on failure .*/
  def run(a: T, b: T, c: T): Unit = {
    waitFor(1, a)
    waitFor(3, b)
    waitFor(5, c)
  }

}

class AkkaRsmTest extends FunSuite {

  test("Akka replicated state machine demo") {
    val system = ActorSystem.create()
    try {
      new RMSDemo[String](system).run("a", "b", "c")
    } finally {
      system.shutdown()
    }
  }

}
