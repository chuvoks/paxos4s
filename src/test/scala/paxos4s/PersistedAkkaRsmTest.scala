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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import scala.collection.SortedSet
import scala.concurrent.Await
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
import java.io.ObjectStreamClass

case class InstanceData[T](id: Int, members: Set[Int], state: PaxosState[T])

class RSMPersisted[T](states: StateInstances[T]) extends Actor {

  def receive = {
    case rsmPax: RsmPax[T] => rsmPax match {
      case RsmPax(stateId, pax) => {
        val instance = states.get(stateId).getOrElse(states.create(stateId))
        instance.process(pax) match {
          case Success(Step(next, Some(Agreed(t)))) => {
            states.update(stateId, next)
          }
          case Success(Step(next, _)) => states.update(stateId, next)
          case a => throw new IllegalStateException("Cannot handle: " + a)
        }
      }
    }
    case get: Get[T] =>
      get.promise.complete(Success({
        states.keys.map(stateId =>
          (stateId -> states.get(stateId).map(instance =>
            instance.agreedValue).flatten)).toMap
      }))
    case t: T @unchecked => {
      val latestState: Option[Long] = (SortedSet[Long]() ++ states.keys).lastOption
      val newStateId = latestState.getOrElse(0L) + 1L
      states.create(newStateId).propose(t.asInstanceOf[T]) match {
        case Success(next) => states.update(newStateId, next)
        case a => throw new IllegalStateException("Cannot handle: " + a)
      }
    }
  }

}

class InMemoryDataPersistence[K, T](cl: ClassLoader) {

  private[this] var blobStore: Map[K, Array[Byte]] = Map()

  def save(key: K, instanceData: InstanceData[T]): Unit = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(instanceData)
    oos.close()
    blobStore += (key -> baos.toByteArray())
  }

  def load(key: K): Option[InstanceData[T]] = {
    blobStore.get(key).map(data => {
      ((new ObjectInputStream(
        new ByteArrayInputStream(data)) {
        // See https://github.com/sbt/sbt/issues/513
        override def resolveClass(desc: ObjectStreamClass): Class[_] = {
          Class.forName(desc.getName(), true, cl);
        }
      })
        .readObject()).asInstanceOf[InstanceData[T]]
    })
  }

  def keys: Set[K] = blobStore.keySet

}

class InstancePersistence[K, T](persistence: InMemoryDataPersistence[K, T]) {

  def get(key: K, send: PaxOut[T] => Unit): Option[Instance[T]] =
    persistence.load(key).map(data => from(key, data, send))

  def update(key: K, instance: Instance[T]): Unit =
    persistence.save(key,
      persistence.load(key).get.copy(state = instance.state))

  def create(key: K, id: Int, members: Set[Int], send: PaxOut[T] => Unit): Instance[T] =
    from(key, InstanceData(id, members, PaxosState.empty), send)

  def keys: Set[K] = persistence.keys

  private[this] def from(
    key: K,
    instanceData: InstanceData[T],
    send: PaxOut[T] => Unit): Instance[T] = {
    val persist: PaxosState[T] => Unit =
      newState => persistence.save(key, InstanceData(instanceData.id, instanceData.members, newState))
    val op = PaxosOp.create(instanceData.id, instanceData.members, persist, send)
    Instance(op, instanceData.state)
  }

}

class StateInstances[T](
  id: Int,
  persist: InstancePersistence[Long, T],
  currentMembers: => Set[Int],
  dispatch: (Int, AnyRef) => Unit) {

  def get(stateId: Long): Option[Instance[T]] =
    persist.get(stateId, send(stateId, _))

  def update(stateId: Long, instance: Instance[T]): Unit =
    persist.update(stateId, instance)

  def create(stateId: Long): Instance[T] =
    persist.create(stateId, id, currentMembers, send(stateId, _))

  private[this] def send(stateId: Long, out: PaxOut[T]): Unit =
    out.dest.foreach(dst => dispatch(dst, RsmPax(stateId, out.pax)))

  def keys: Set[Long] = persist.keys

}

class PersistedRMSDemo[T](val system: ActorSystem, cl: ClassLoader) {

  private[this] val members: Set[Int] = (1 to 10).toSet

  private[this] val actors: Map[Int, ActorRef] = members.map(id => {
    val persist = new InstancePersistence[Long, T](new InMemoryDataPersistence(cl))
    val states = new StateInstances(id, persist, members,
      dispatch = (dst: Int, msg: AnyRef) => actors(dst) ! msg)
    (id -> system.actorOf(Props(new RSMPersisted[T](states))))
  }).toMap

  private[this] def get: Future[Map[Long, Option[T]]] = {
    val getReq = Get(ScalaPromise[Map[Long, Option[T]]]())
    actors(2) ! getReq
    getReq.promise.future
  }

  def waitFor(x: T): Unit = {
    import scala.concurrent.duration._
    while (Await.result(get, Duration("3 seconds")).filter(_._2 == Some(x)).isEmpty) {
      Thread.`yield`()
    }
  }

  /** throws timeout exception on failure .*/
  def run(a: T, b: T, c: T): Unit = {
    actors(1) ! a
    waitFor(a)
    actors(3) ! b
    waitFor(b)
    actors(5) ! c
    waitFor(c)
  }

}

class PersistedAkkaRsmTest extends FunSuite {

  test("Akka replicated state machine demo, persisted") {
    val system = ActorSystem.create()
    try {
      // See https://github.com/sbt/sbt/issues/513
      def cl: ClassLoader = classOf[PersistedRMSDemo[_]].getClassLoader()
      new PersistedRMSDemo[String](system, cl).run("a", "b", "c")
    } finally {
      system.shutdown()
    }
  }

}

