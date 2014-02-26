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

import org.scalatest.FunSuite

import Simulation.exec

class InstanceOtherTest extends FunSuite {

  test("fuzzing, small'ish quorum") {
    for (i <- 1 to 1000) {
      val size = Random.nextInt(6) + 3
      val min = PaxosOp.minQuorum(size)
      val quorum = min + Random.nextInt(size - min) / 2
      exec(size, quorum, optimize = Random.nextBoolean, hazardous = Random.nextBoolean)
    }
  }

  test("fuzzing, minimum quorum") {
    for (i <- 1 to 2000) {
      val size = Random.nextInt(5) + 3
      val quorum = PaxosOp.minQuorum(size)
      exec(size, quorum, optimize = Random.nextBoolean, hazardous = Random.nextBoolean)
    }
  }

  test("too small quorum must fail") {
    intercept[IllegalArgumentException] {
      val size = Random.nextInt(10) + 3
      val quorum = PaxosOp.minQuorum(size) - 1
      exec(size, quorum, optimize = Random.nextBoolean, hazardous = Random.nextBoolean)
    }
  }

}
