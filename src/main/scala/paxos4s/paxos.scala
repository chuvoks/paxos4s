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

import scala.util.Success
import scala.util.Try

import org.slf4j.LoggerFactory

import com.typesafe.scalalogging.slf4j.Logger

/**
 * Proposal number
 * @constructor Creates a new proposal number
 * @param n round number
 * @param id unique id of the processor
 */
final case class N(val n: Int, val id: Int) extends Ordered[N] {
  private def value = (n.toLong << Integer.SIZE) | id
  override def compare(that: N) = this.value compare that.value
}
final object N { val epoc = N(0, 0) }

/**
 * Paxos protocol message.
 * @param srcId id of the message sender
 */
sealed abstract class Pax[T] extends Serializable with Ordered[Pax[T]] {
  val srcId: Int
  protected val prio: Int
  override def compare(that: Pax[T]): Int = this.prio compare that.prio // TODO: finally check srcId?
}
final case class Prepare[T](val srcId: Int, val n: N) extends Pax[T] { val prio = 0 }
final case class Nack[T](val srcId: Int, val n: N) extends Pax[T] { val prio = 1 }
final case class Promise[T](val srcId: Int, val rnd: N, val vrnd: N, val vval: Option[T]) extends Pax[T] { val prio = 2 }
final case class AcceptReq[T](val srcId: Int, val crnd: N, val cval: T) extends Pax[T] { val prio = 3 }
final case class Accepted[T](val srcId: Int, val n: N, val v: T) extends Pax[T] { val prio = 4 }
final case class Learn[T](val srcId: Int, val v: T) extends Pax[T] { val prio = 5 } // force learn value

// light weight version of Accepted
final case class AcceptedLight[T](val srcId: Int, val n: N)

/** Output packet*/
final case class PaxOut[T](val pax: Pax[T], val dest: Set[Int])

/**
 * Paxos instance properties and operations
 *
 * @param id unique identifier for this member.
 * @param otherIds other instance members.
 * @param send function to send output to other instance members.
 * Implementation can operate on "fire-and-forget" basis. Throwing exceptions is not recommended.
 * @param persist function must persist paxos protocol state information.
 * This function should be reliable.
 * @param optimize should be true by default. Set to false for debugging and testing purposes.
 */
final case class PaxosOp[T](
  val id: Int,
  val quorum: Int,
  val otherIds: Set[Int], // predicate to check if pax.srcId is acceptable
  val send: PaxOut[T] => Unit, // can fail silently as much as it wants
  val persist: PaxosState[T] => Unit, // must not fail silently
  val optimize: Boolean) { // true to short circuit once value is learned, false to debug the protocol

  require(quorum >= PaxosOp.minQuorum(otherIds.size + 1) && quorum <= PaxosOp.maxQuorum(otherIds.size + 1),
    s"quorum=$quorum must be >= ${PaxosOp.minQuorum(otherIds.size + 1)} and <= ${PaxosOp.maxQuorum(otherIds.size + 1)}")

}

final case class PaxosState[T](
  val rnd: N, // highest proposal number participated
  val vrnd: N, // highest proposal number voted
  val vval: Option[T], // value voted with proposal number vrnd
  val crnd: N, // coordinator: highest proposal number begun
  val cval: Option[T], // coordinator: highest value for proposal number crnd
  val learnedVal: Option[T],
  // transient members
  val pval: Option[T], // proposed value
  val receivedPromises: Set[Promise[T]],
  val receivedAccepts: Set[AcceptedLight[T]]) {

  def isProposing = pval.isDefined
  def isLearning = learnedVal.isEmpty

  def describe: String = s"rnd=$rnd, vrnd=$vrnd, vval=$vval, crnd=$crnd, cval=$cval, pval=$pval, learnedVal=$learnedVal, receivedPromises=$receivedPromises, receivedAccepts=$receivedAccepts"

}

/** Implements basic paxos. */
final object PaxosState {

  private[this] val log = Logger(LoggerFactory.getLogger(PaxosState.getClass()))

  def empty[T] = PaxosState[T](
    rnd = N.epoc,
    vrnd = N.epoc,
    vval = None,
    crnd = N.epoc,
    cval = None,
    learnedVal = None,
    pval = None,
    receivedPromises = Set.empty,
    receivedAccepts = Set.empty)

  /** Empty paxos state with learned value. */
  private[this] def forceLearn[T](t: T) = empty.copy(learnedVal = Some(t))

  def propose[T](paxos: PaxosOp[T])(state: PaxosState[T], value: T): Try[PaxosState[T]] =
    if (null == value) {
      throw new NullPointerException("Value was null")
    } else if (state.isLearning) {
      /**
       * Phase 1a: Prepare
       *
       * A Proposer creates a proposal identified with a number N. This
       * number must be greater than any previous proposal number used by
       * this Proposer. Then, it sends a Prepare message containing this
       * proposal to a Quorum of Acceptors.
       */
      val round = N(state.rnd.n + 1, paxos.id) // TODO check for integer overflow?
      val s = state.copy(
        rnd = round,
        crnd = round,
        pval = Some(value))
      val out = PaxOut(Prepare[T](paxos.id, round), paxos.otherIds)
      log.trace(s"PROPOSE id=${paxos.id} ${s.describe}")
      Try({
        paxos.persist(s)
        paxos.send(out)
        s
      })
    } else throw new IllegalStateException("Value is already learned") // TODO or is Failure better option?

  def handle[T](paxos: PaxosOp[T])(state: PaxosState[T])(p: Pax[T]): Try[PaxosState[T]] = p match {
    case _: Learn[_] if paxos.otherIds.contains(p.srcId) => doHandle(paxos, state)(p) // force learn value
    case _ if paxos.optimize && !state.isLearning && paxos.otherIds.contains(p.srcId) => {
      // Force sender to learn if we've already learned.
      log.trace(s"optimizing id=${paxos.id} dest=${p.srcId}")
      Try({
        paxos.send(PaxOut(Learn(paxos.id, state.learnedVal.get), Set(p.srcId)))
        state
      })
    }
    case _ if paxos.otherIds.contains(p.srcId) => doHandle(paxos, state)(p)
    case _ => Success(state) // paxos.id == p.srcId
  }

  private[this] def doHandle[T](paxos: PaxosOp[T], state: PaxosState[T])(p: Pax[T]): Try[PaxosState[T]] = p match {
    case Prepare(srcId, n) => {
      /**
       * Phase 1b: Promise
       *
       * If the proposal's number N is higher than any previous
       * proposal number received from any Proposer by the Acceptor,
       * then the Acceptor must return a promise to ignore all future
       * proposals having a number less than N. If the Acceptor
       * accepted a proposal at some point in the past, it must
       * include the previous proposal number and previous value in
       * its response to the Proposer.
       */
      if (n > state.rnd) {
        val s = state.copy(rnd = n)
        val out = PaxOut(Promise(paxos.id, n, state.vrnd, state.vval), Set(srcId))
        log.trace(s"PREPARE id=${paxos.id} ${s.describe}")
        Try({
          paxos.persist(s)
          paxos.send(out)
          s
        })
      } else if (n < state.rnd) {
        /**
         * Otherwise, the Acceptor can ignore the received proposal.
         * It does not have to answer in this case for Paxos to work.
         * However, for the sake of optimization, sending a Nack will
         * tell the Proposer that it can stop its attempt to create
         * consensus with proposal N.
         */
        log.trace(s"PREPARE REJECT id=${paxos.id} ${state.describe}")
        Try({
          paxos.send(PaxOut(Nack[T](paxos.id, n), Set(srcId)))
          state
        })
      } else {
        Success(state) // n == state.rnd --> NOP
      }
    }
    case that: Promise[T] => {
      /**
       * Phase 2a: Accept Request
       *
       * If a Proposer receives enough promises from quorum of
       * Acceptors, it needs to set a value to its proposal. If any
       * Acceptors had previously accepted any proposal, then they'll
       * have sent their values to the Proposer, who now must set the
       * value of its proposal to the value associated with the
       * highest proposal number reported by the Acceptors. If none of
       * the Acceptors had accepted a proposal up to this point, then
       * the Proposer may choose any value for its proposal.
       *
       * The Proposer sends an Accept Request message to a quorum of
       * Acceptors with the chosen value for its proposal.
       */
      // require (state.crnd == state.rnd) because Propose and AcceptReq have it also 
      if (state.isProposing && state.crnd == that.rnd && state.crnd == state.rnd) {
        val promises = (state.receivedPromises).filter(_.rnd == state.crnd) + that
        if (promises.size >= paxos.quorum) {
          val maxVrnd = promises.maxBy(_.vrnd)
          val value = maxVrnd.vval.orElse(state.pval)
          val promiserIds = promises.map(_.srcId)
          val s = state.copy(
            cval = value,
            pval = None,
            receivedPromises = Set.empty[Promise[T]])
          // TODO could send only to promiserIds. Needs some performance testing.
          val out = PaxOut(AcceptReq(paxos.id, state.crnd, value.get), paxos.otherIds) // eagerly send to all
          log.trace(s"PROMISE id=${paxos.id} ${s.describe} promiserIds=$promiserIds")
          Try({
            paxos.persist(s)
            paxos.send(out)
            s
          })
        } else {
          Success(state.copy(receivedPromises = promises))
        }
      } else {
        Success(state)
      }
    }
    case Nack(srcId, n) => {
      if (state.crnd == n) {
        val s = state.copy(
          pval = None,
          receivedPromises = Set.empty[Promise[T]])
        log.trace(s"NACK id=${paxos.id} ${s.describe}")
        Try({
          paxos.persist(s) // not strictly required
          s
        })
      } else {
        Success(state)
      }
    }
    case AcceptReq(srcId, crnd, cval) => {
      /**
       * Phase 2b: Accepted
       *
       * If an Acceptor receives an Accept Request message for a
       * proposal N, it must accept it if and only if it has not
       * already promised to only consider proposals having an
       * identifier greater than N. In this case, it should register
       * the corresponding value v and send an Accepted message to the
       * Proposer and every Learner. Else, it can ignore the Accept
       * Request.
       */
      if (crnd == state.rnd) { // require that we have promised
        val s = state.copy(
          vrnd = crnd,
          vval = Some(cval))
        // without optimization this can easily choke the network
        val dst = if (paxos.optimize) Set(srcId) else paxos.otherIds
        val out = PaxOut(Accepted[T](paxos.id, crnd, cval), dst)
        log.trace(s"ACCEPT REQ id=${paxos.id} ${s.describe}")
        Try({
          paxos.persist(s)
          paxos.send(out)
          s
        })
      } else {
        Success(state)
      }
    }
    case Accepted(srcId, n, v) => {
      /*
       * There can be concurrent proposals which reach majority.
       * Some of the resulting AcceptReq get processed leading to 
       * Accepted messages.
       * Since eventual consistency is not desired we require a 
       * majority. This guarantees that only one value is learned.
       */
      val accepts = state.receivedAccepts + AcceptedLight(srcId, n)
      if (state.rnd == n && accepts.filter(_.n == n).size >= paxos.quorum) {
        if (state.learnedVal.isDefined && state.learnedVal != Some(v)) {
          throw new IllegalStateException(s"id=${paxos.id}, Accepted($srcId, $n, $v), ${state.learnedVal} != Some($v), ${state.describe}")
        }
        val s = {
          if (paxos.optimize) forceLearn(v)
          else state.copy(
            learnedVal = Some(v),
            receivedAccepts = accepts) // could be set to empty but kept as a debug safe guard
        }
        log.trace(s"ACCEPTED id=${paxos.id} ${s.describe}")
        Try({
          paxos.persist(s)
          // s.crnd is potentially optimized to N(0,0), use state.crnd
          if (paxos.optimize && state.crnd == n) {
            // TODO Could this be sent only to smaller subset
            log.trace(s"coordinator optimizes learn id=${paxos.id}")
            paxos.send(PaxOut(Learn(paxos.id, s.learnedVal.get), paxos.otherIds))
          }
          s
        })
      } else {
        Success(state.copy(receivedAccepts = accepts))
      }
    }
    case Learn(srcId, value) => {
      if (state.isLearning) {
        log.trace(s"LEARN id=${paxos.id} value=$value")
        val s = if (paxos.optimize) forceLearn(value)
        else state.copy(learnedVal = Some(value))
        Try({
          paxos.persist(s)
          s
        })
      } else if (value != state.learnedVal.get) {
        throw new IllegalStateException(s"Learn conflict: existing value: ${state.learnedVal.get} new value: $value")
      } else {
        Success(state)
      }
    }
  }

  /** Removes: proposed value, received promises and received accepts. */
  def clearTransient[T](state: PaxosState[T]) = state.copy(
    pval = None,
    receivedPromises = Set.empty[Promise[T]],
    receivedAccepts = Set.empty[AcceptedLight[T]])
}

final object PaxosOp {

  def create[T](id: Int, members: Set[Int], persist: PaxosState[T] => Unit, send: PaxOut[T] => Unit): PaxosOp[T] =
    PaxosOp[T](id, quorum = PaxosOp.minQuorum(members.size), members - id, send, persist, optimize = true)

  /**
   * Algorithm can make progress using 2F+1 processors despite the
   * simultaneous failure of any F processors.
   * @param cluster size. This should be the same as cluster members.size.
   * @throws IllegalArgumentException if cluster size is less than three
   */
  def minQuorum(clusterSize: Int): Int = clusterSize match {
    case size if size > 2 => size / 2 + 1
    case _ => throw new IllegalArgumentException(s"Size=$clusterSize must be greater than 2")
  }

  def maxQuorum(clusterSize: Int): Int = clusterSize match {
    case size if size > 2 => clusterSize - 1
    case _ => throw new IllegalArgumentException(s"Size=$clusterSize must be greater than 2")
  }

}
