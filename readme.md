paxos4s
=======

Paxos (https://en.wikipedia.org/wiki/Paxos_%28computer_science%29) implementation in Scala.

In a nutshell: Paxos is a fault tolerant consensus protocol.
It can make progress using 2F+1 processors despite the simultaneous failure of any F processors.
If there is more than F simultaneous failing processors consensus is not possible.

Current implement is collapsed basic Paxos with some optimizations and tweaks.
Multi-Paxos is not yet implemented.


API
---

Purely functional and immutable, straight forward and simple:
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


Examples
--------

https://github.com/TODO
https://github.com/TODO


Reduced connectivity
--------------------

In collapsed basic Paxos each node is Client, Proposer, Acceptor and Learner. This can lead to
a fully connected network requiring ```c = n * (n - 1) / 2``` connections.

Implementation is optimized to limit only proposer to communicate with others.


Atomic & consistent
-------------------

Paxos protocol is eventually consistent: there can be multiple concurrent conflicting *Accepted* messages in-flight.
Implementation requires *Accepted* message from majority which makes it effectively atomic.


Short-circuiting
----------------

Since the implementation is atomic it short-circuits immediately after value is agreed forcing
other members to agree on value if they attempt any further communication.

Notice that this also allows implementation to forget all the other protocol state information 
once the consensus is reached: only agreed value needs to be kept.


Requirements
------------

* reliable persistent storage
* corruption free message transport


Other considerations
--------------------

* Despite of optimizations collapsed basic Paxos protocol has its limit on scalability.
* Minimum number of processors is 3 (Paxos can make progress using 2F+1 processor with F simultaneous failures).
* Having 5 - 32 processors seems to be fairly reasonable amount.
* Reaching agreement with 10.000 processors can take several seconds even within a single jvm.
* Network/Paxos congestion can kill the performance. Use message throttling if necessary.


Note on persistence
-------------------

Instance data should be kept intact despite restarts and crashes.
This includes instance id, other member ids and paxos state.
Adding or removing members after first instance useage can compromise the protocol.

See this example:
https://github.com/TODO


License
-------

Licensed under Apache License, Version 2.0.
