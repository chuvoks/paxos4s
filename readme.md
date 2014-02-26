paxos4s
=======

[Paxos](https://en.wikipedia.org/wiki/Paxos_%28computer_science%29) implementation in Scala.

In a nutshell: Paxos is a fault tolerant consensus protocol.
It can make progress using 2F+1 processors despite the simultaneous failure of any F processors.

Current implement is Collapsed Multi-Paxos.


API
---

Purely functional, immutable and simple:
* propose method for persuading others
* process method which takes a message and returns an optional outcome (agreed/rejected)

```scala
/** Paxos protocol instance. */
final case class Instance[T](...) {

  def agreedValue: Option[T] = ...

  def propose(value: T): Try[Instance[T]] = ...

  def process(pax: Pax[T]): Try[Step[T]] = ...

}

sealed trait Outcome[+T]
final case object Rejected extends Outcome[Nothing]
final case class Agreed[T](val t: T) extends Outcome[T]

final case class Step[T](
  val next: Instance[T],
  val outcome: Option[Outcome[T]])
```
In action:
```scala
class MyActor[T](var instance: Instance[T]) extends Actor {

  def receive = {
    case pax: Pax[T] => instance.process(pax) match {
      case Success(Step(next, Some(Agreed(t)))) => {
        println("Agreed: " + t + "!")
      }
      case Success(Step(next, _)) => instance = next
    }
  }

}
```

While the main API is trivial to use, setting up the persistence and message delivery can take some effort. See the examples below.

Examples
--------

* https://github.com/chuvoks/paxos4s/blob/master/src/test/scala/paxos4s/AkkaConsensusTest.scala
* https://github.com/chuvoks/paxos4s/blob/master/src/test/scala/paxos4s/AkkaRsmTest.scala
* https://github.com/chuvoks/paxos4s/blob/master/src/test/scala/paxos4s/PersistedAkkaRsmTest.scala


Short-circuiting
----------------

Implementation short-circuits immediately after a value is agreed. This forces
other members to agree on the value if they attempt any further communication.

After short-circuiting only the learned value and selected leader identifier are kept.
Persistence implementations can take advantage of this, if desired.


Leader ship detection
---------------------

Leader is simply the processor whose proposal gets accepted. This can be used, for example, to enable Multi-Paxos.


Multi-Paxos implementation
--------------------------

Optional leader identifier can be given when paxos instances are created.

If leader identifier is given the resulting protocol instance is rigged as if the first phase of the protocol is already completed by the given leader.
When rigged leader invokes propose method it will send immediately Accept Request to acceptors.
Similarly rigged acceptors will immediately reply with Accepted message.

If leader is not given or some of the acceptors make a proposal the protocol uses Basic-Paxos.


Few things to keep in mind
--------------------------

* Minimum number of processors is 3 (Paxos can make progress using 2F+1 processor with F simultaneous failures).
* Having 5 - 32 processors seems to be fairly reasonable amount.
* Reaching consensus with 10.000 processors can take several seconds even within a single jvm. Multi-Paxos reduces execution time to roughly half on following rounds, assuming the leader do not change.
* Network/Paxos congestion can kill the performance. Use message throttling if necessary. Having a stable leader + Multi-Paxos helps.


Persisted data
--------------

Instance data should be kept intact despite restarts and crashes. This includes instance id, other member identifiers and paxos protocol state.

Adding or removing members during paxos instance process will compromise the protocol. Any membership changes should happen between different paxos instances.
In other words: after paxos instance is created do not add/remove/modify member identifiers. Ever.

See the PersistedAkkaRsmTest example mentioned above.


License
-------

Licensed under Apache License, Version 2.0.
