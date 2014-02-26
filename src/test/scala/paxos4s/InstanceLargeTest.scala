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

class InstanceLargeTest extends FunSuite {

  test("cluster of 64") {
    exec(size = 64, quorum = PaxosOp.minQuorum(64), optimize = true, hazardous = false)
  }

  test("cluster of 128") {
    exec(size = 128, quorum = PaxosOp.minQuorum(128), optimize = true, hazardous = false)
  }

  test("cluster of 256") {
    exec(size = 256, quorum = PaxosOp.minQuorum(256), optimize = true, hazardous = false)
  }

  test("cluster of 512") {
    exec(size = 512, quorum = PaxosOp.minQuorum(512), optimize = true, hazardous = false)
  }

  test("cluster of 1024") {
    exec(size = 1024, quorum = PaxosOp.minQuorum(1024), optimize = true, hazardous = false)
  }

  ignore("cluster of 2048") {
    exec(size = 2048, quorum = PaxosOp.minQuorum(2048), optimize = true, hazardous = false)
  }

  ignore("cluster of 4096") {
    exec(size = 4096, quorum = PaxosOp.minQuorum(4096), optimize = true, hazardous = false)
  }

}
