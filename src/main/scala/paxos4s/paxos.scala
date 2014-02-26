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
  private def value = (n.toLong << Integer.SIZE) | (id & 0xffffffffL)
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
final case class Promise[T](val srcId: Int, val rnd: N, val vrnd: N, val vval: Option[(T, Int)]) extends Pax[T] { val prio = 2 }
final case class AcceptReq[T](val srcId: Int, val crnd: N, val cval: (T, Int)) extends Pax[T] { val prio = 3 }
final case class Accepted[T](val srcId: Int, val n: N) extends Pax[T] { val prio = 4 }
final case class Learn[T](val srcId: Int, val v: (T, Int)) extends Pax[T] { val prio = 5 } // force learn value

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

final case class PaxosState[T](
  val rnd: N, // highest proposal number participated
  val vrnd: N, // highest proposal number voted
  val vval: Option[(T, Int)], // value voted with proposal number vrnd
  val crnd: N, // coordinator: highest proposal number begun
  val learnedData: Option[(T, Int)], // value and original proposer id
  // transient members
  val pval: Option[(T, Int)], // proposed value + leaderId
  val receivedPromises: Set[Promise[T]],
  val receivedAccepts: Set[Accepted[T]]) {

  def learnedVal: Option[T] = learnedData.map(_._1)
  def leader: Option[Int] = learnedData.map(_._2)

  def isProposing = pval.isDefined
  def isLearning = learnedData.isEmpty

  def describe: String = s"rnd=$rnd, vrnd=$vrnd, vval=$vval, crnd=$crnd, pval=$pval, learnedData=$learnedData, receivedPromises=$receivedPromises, receivedAccepts=$receivedAccepts"

}

/**
 * Implements Collapsed Basic Paxos and Collapsed Multi-Paxos.
 *
 * To use Multi-Paxos see [[PaxosState.leaderRiggedToMultiPaxos]] and
 * [[PaxosState.nonLeaderRiggedToMultiPaxos]].
 */
final object PaxosState {

  private[this] val log = Logger(LoggerFactory.getLogger(PaxosState.getClass()))

  def empty[T] = PaxosState[T](
    rnd = N.epoc,
    vrnd = N.epoc,
    vval = None,
    crnd = N.epoc,
    learnedData = None,
    pval = None,
    receivedPromises = Set.empty,
    receivedAccepts = Set.empty)

  /**
   * Removes: proposed value, received promises and received accepts.
   *
   * Notice: removal of received promises disables Multi-Paxos optimization, if enabled.
   */
  def clearTransient[T](state: PaxosState[T]) =
    state.copy(
      pval = None,
      receivedPromises = Set.empty[Promise[T]],
      receivedAccepts = Set.empty[Accepted[T]])

  /** An empty state which is set as if proposal is made and promises where received from other members. */
  def leaderRiggedToMultiPaxos[T](leaderId: Int, otherIds: Set[Int]) =
    empty[T].copy(
      rnd = N(1, leaderId),
      crnd = N(1, leaderId),
      receivedPromises = otherIds.map(oid => Promise[T](oid, N(1, leaderId), N.epoc, None)))

  /** An empty state which is set as if promised to the given leader. */
  def nonLeaderRiggedToMultiPaxos[T](leaderId: Int) =
    empty[T].copy(rnd = N(1, leaderId))

  /** Empty paxos state with learned value. */
  private[this] def forceLearn[T](t: (T, Int)) =
    empty.copy(learnedData = Some(t))

  private[this] def persistSend[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    out: PaxOut[T]): Try[PaxosState[T]] = Try {
    paxos.persist(state)
    paxos.send(out)
    state
  }

  private[this] def persistSendSend[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    out1: PaxOut[T],
    out2: PaxOut[T]): Try[PaxosState[T]] = Try {
    paxos.persist(state)
    paxos.send(out1)
    paxos.send(out2)
    state
  }

  private[this] def persist[T](
    paxos: PaxosOp[T],
    state: PaxosState[T]): Try[PaxosState[T]] = Try {
    paxos.persist(state)
    state
  }

  private[this] def send[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    out: PaxOut[T]): Try[PaxosState[T]] = Try {
    paxos.send(out)
    state
  }

  /**
   * Phase 1a: Prepare
   *
   * A Proposer creates a proposal identified with a number N. This
   * number must be greater than any previous proposal number used by
   * this Proposer. Then, it sends a Prepare message containing this
   * proposal to a Quorum of Acceptors.
   */
  def propose[T](paxos: PaxosOp[T])(state: PaxosState[T], value: T): Try[PaxosState[T]] =
    if (null == value) {
      throw new NullPointerException("Value was null")
    } else if (state.isLearning) {

      def isMultiPaxosLeader: Boolean = {
        val firstRnd = N(1, paxos.id)
        state.rnd == firstRnd &&
          state.crnd == firstRnd &&
          state.receivedPromises.filter(_.rnd == state.crnd).size >= paxos.quorum
      }

      if (isMultiPaxosLeader) {
        prepareAcceptReq(paxos, state, (value, paxos.id), "MULTI-PAXOS (PROMISE)")
      } else {
        val nextRnd = N(state.rnd.n + 1, paxos.id) // TODO check for integer overflow?
        val s = state.copy(
          rnd = nextRnd,
          crnd = nextRnd,
          pval = Some((value, paxos.id)))
        val out = PaxOut(Prepare[T](paxos.id, nextRnd), paxos.otherIds)
        log.trace(s"PROPOSE id=${paxos.id} ${s.describe}")
        persistSend(paxos, s, out)
      }
    } else throw new IllegalStateException("Value is already learned") // TODO or is Failure better option?

  private[this] def prepareAcceptReq[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    value: (T, Int),
    logMsg: => String): Try[PaxosState[T]] = {
    val s = state.copy(
      vrnd = state.crnd, // we have effectively voted for this
      vval = Some(value),
      pval = None,
      receivedPromises = Set.empty[Promise[T]])
    // TODO could send only to promiserIds. Needs some performance testing.
    log.trace(s"$logMsg id=${paxos.id} ${s.describe}")
    val acceptReqOut = PaxOut(AcceptReq(paxos.id, state.crnd, value), paxos.otherIds)
    if (paxos.optimize) persistSend(paxos, s, acceptReqOut)
    else {
      // let others know we've accepted (needed to get a quorum of accepts to learn the value)
      log.trace(s"PROPOSER ACCEPT REQ id=${paxos.id} ${s.describe}")
      val acceptedOut = PaxOut(Accepted[T](paxos.id, s.crnd), paxos.otherIds)
      persistSendSend(paxos, s, acceptedOut, acceptReqOut)
    }

  }

  def handle[T](paxos: PaxosOp[T], state: PaxosState[T], p: Pax[T]): Try[PaxosState[T]] = {
    if (paxos.otherIds.contains(p.srcId)) {
      if (paxos.optimize && !state.isLearning && !p.isInstanceOf[Learn[_]]) { // Force sender to learn
        log.trace(s"optimizing id=${paxos.id} dest=${p.srcId}")
        send(paxos, state, PaxOut(Learn(paxos.id, state.learnedData.get), Set(p.srcId)))
      } else {
        processPax(paxos, state, p)
      }
    } else {
      Success(state) // most likely paxos.id == p.srcId
    }
  }

  private[this] def processPax[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    p: Pax[T]): Try[PaxosState[T]] = p match {
    case p: Prepare[T] => prepare(paxos, state, p)
    case p: Promise[T] => promise(paxos, state, p)
    case p: Nack[T] => nack(paxos, state, p)
    case p: AcceptReq[T] => acceptReq(paxos, state, p)
    case p: Accepted[T] => accepted(paxos, state, p)
    case p: Learn[T] => learn(paxos, state, p)
  }

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
   *
   * Otherwise, the Acceptor can ignore the received proposal.
   * It doesn't have to answer in this case for Paxos to work.
   * However, for the sake of optimization, sending a Nack will
   * tell the Proposer that it can stop its attempt to create
   * consensus with proposal N.
   */
  private[paxos4s] def prepare[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    prepare: Prepare[T]): Try[PaxosState[T]] = {
    if (prepare.n > state.rnd) {
      val s = state.copy(rnd = prepare.n)
      val out = PaxOut(Promise(paxos.id, prepare.n, state.vrnd, state.vval), Set(prepare.srcId))
      log.trace(s"PREPARE id=${paxos.id} ${s.describe}")
      persistSend(paxos, s, out)
    } else if (prepare.n < state.rnd) {
      log.trace(s"PREPARE REJECT id=${paxos.id} ${state.describe}")
      send(paxos, state, PaxOut(Nack[T](paxos.id, prepare.n), Set(prepare.srcId)))
    } else {
      Success(state) // n == state.rnd --> NOP
    }
  }

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
  private[paxos4s] def promise[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    promise: Promise[T]): Try[PaxosState[T]] = {
    // require (state.crnd == state.rnd) because Propose and AcceptReq have it also 
    if (state.isProposing && state.crnd == promise.rnd && state.crnd == state.rnd) {
      val promises = (state.receivedPromises).filter(_.rnd == state.crnd) + promise
      if (promises.size >= paxos.quorum) {
        val maxVrnd = promises.maxBy(_.vrnd)
        val value = maxVrnd.vval.orElse(state.pval).get
        val promiserIds = promises.map(_.srcId)
        prepareAcceptReq(paxos, state, value, s"PROMISE promiserIds=$promiserIds")
      } else {
        Success(state.copy(receivedPromises = promises))
      }
    } else {
      Success(state)
    }
  }

  /**
   * The proposer should abandon its proposal.
   */
  private[paxos4s] def nack[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    nack: Nack[T]): Try[PaxosState[T]] = {
    if (state.crnd == nack.n) {
      val s = state.copy(
        pval = None,
        receivedPromises = Set.empty[Promise[T]])
      log.trace(s"NACK id=${paxos.id} ${s.describe}")
      Success(s)
    } else {
      Success(state)
    }
  }

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
  private[paxos4s] def acceptReq[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    ar: AcceptReq[T]): Try[PaxosState[T]] = {
    if (ar.crnd >= state.rnd) { // require that we have promised
      val s = state.copy(
        rnd = ar.crnd,
        vrnd = ar.crnd,
        vval = Some(ar.cval))
      // without optimization this can easily choke the network
      val dst = if (paxos.optimize) Set(ar.srcId) else paxos.otherIds
      val out = PaxOut(Accepted[T](paxos.id, ar.crnd), dst)
      log.trace(s"ACCEPT REQ id=${paxos.id} ${s.describe}")
      persistSend(paxos, s, out)
    } else {
      Success(state)
    }
  }

  /**
   * To learn that a value has been chosen, a learner must find out that
   * a proposal has been accepted by a majority of acceptors
   */
  private[paxos4s] def accepted[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    accepted: Accepted[T]): Try[PaxosState[T]] = {
    val n = accepted.n
    val srcId = accepted.srcId
    val accepts = state.receivedAccepts + accepted

    def hasProposed = state.rnd == state.crnd

    def hasAccepted = state.rnd == state.vrnd

    def hasQuorum = accepts.filter(_.n == n).size >= paxos.quorum

    def votedValue = {
      if (state.vval.isEmpty)
        throw new IllegalStateException(s"id=${paxos.id}, $accepted, value is not defined, ${state.describe}")
      else if (state.learnedData.isDefined && state.learnedData != state.vval)
        throw new IllegalStateException(s"id=${paxos.id}, $accepted, learned value conflicts with voted value, ${state.describe}")
      state.vval.get
    }

    if (state.rnd == n && (hasProposed || hasAccepted) && hasQuorum) {
      val v: (T, Int) = votedValue
      val nextState: PaxosState[T] = {
        if (paxos.optimize) forceLearn(v)
        else state.copy(
          learnedData = Some(v),
          receivedAccepts = accepts) // could be set to empty but kept as a debug safe guard
      }
      log.trace(s"ACCEPTED id=${paxos.id} ${nextState.describe}")
      Try {
        paxos.persist(nextState)
        // s.crnd is potentially optimized to N(0,0), use state.crnd
        if (paxos.optimize && hasProposed) {
          // TODO Could this be sent only to smaller subset?
          log.trace(s"proposer optimizes learn id=${paxos.id}")
          paxos.send(PaxOut(Learn(paxos.id, v), paxos.otherIds))
        }
        nextState
      }
    } else {
      Success(state.copy(receivedAccepts = accepts))
    }
  }

  private[paxos4s] def learn[T](
    paxos: PaxosOp[T],
    state: PaxosState[T],
    l: Learn[T]): Try[PaxosState[T]] = {
    val value = l.v
    if (state.isLearning) {
      log.trace(s"LEARN id=${paxos.id} value=${value}")
      val s =
        if (paxos.optimize) forceLearn(value)
        else state.copy(learnedData = Some(value))
      persist(paxos, s)
    } else if (value != state.learnedData.get) {
      throw new IllegalStateException(s"Learn conflict: existing value: ${state.learnedData.get} new value: $value")
    } else {
      Success(state)
    }
  }

}
