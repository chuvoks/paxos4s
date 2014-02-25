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

import scala.util.Try

sealed trait Outcome[+T]
/** Rejected happens if proposal is rejected. */
final case object Rejected extends Outcome[Nothing]
/** Consensus can Agreed exactly once during paxos consensus protocol. */
final case class Agreed[T](val t: T) extends Outcome[T]

final case class Step[T](
  val next: Instance[T],
  val outcome: Option[Outcome[T]])

/** Paxos consensus protocol instance. */
final case class Instance[T](
  val paxos: PaxosOp[T],
  val state: PaxosState[T]) {

  def agreedValue: Option[T] = state.learnedVal

  /**
   * Try to convince others to agree on given value.
   * Calling this method multiple times is safe. For example, to retry rejected proposal
   * or to propose some NOP (no-operation) value simply to see if others have already
   * learned value for this instance. However, adding some random delay before retrying this
   * method is recommended to avoid congestion.
   * @return next instance which should be used for further processing.
   */
  def propose(value: T): Try[Instance[T]] =
    PaxosState.propose(paxos)(state, value).map(Instance(paxos, _))

  /** Process input packet and return next instance which should process the next packet. */
  def process(pax: Pax[T]): Try[Step[T]] = {
    val consensus = PaxosState.handle(paxos)(state)(pax).map(Instance(paxos, _))
    consensus.map(newInstance => {
      val learnTransition = (state.learnedVal, newInstance.state.learnedVal)
      val agreed = learnTransition match {
        case (None, Some(s)) => Some(Agreed(s))
        case _ => None
      }
      val nackTransition = (state.isProposing, !newInstance.state.isProposing, pax)
      val rejected: Option[Outcome[T]] = nackTransition match {
        case (true, false, _: Nack[T]) => Some(Rejected)
        case _ => None
      }
      Step(newInstance, agreed.orElse(rejected))
    })
  }

}

object Instance {

  /**
   * Creates an empty paxos instance.
   *
   * @param unique member identifier
   * @param members identifiers of all members participating into consensus.
   * Notice that this set must remain the same even after crash recovery.
   * @param persist a function which is used to save paxos protocol state into persistent storage.
   * '''It is highly recommended that this function is atomic/transactional.'''
   * Implementation might also find [[paxos4s.PaxosState.clearTransient]] useful.
   * @param send a function which is used to send information to other instance members.
   * This function can behave on "fire-and-forget" basis. Throwing exceptions is not encouraged.
   */
  def empty[T](id: Int, members: Set[Int], persist: PaxosState[T] => Unit, send: PaxOut[T] => Unit): Instance[T] = {
    require(members.contains(id), "members must contain id")
    val op = PaxosOp.create[T](id, members, persist, send)
    val s = PaxosState.empty[T]
    Instance(op, s)
  }

}
