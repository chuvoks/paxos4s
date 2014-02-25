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

import org.scalatest.FunSuite

import Simulation.exec

class Instance12Test extends FunSuite {

  test("cluster of 12,  7 o h") {
    exec(size = 12, quorum = 7, optimize = true, hazardous = true)
  }

  test("cluster of 12,  7 - h") {
    exec(size = 12, quorum = 7, optimize = false, hazardous = true)
  }

  test("cluster of 12,  7 - -") {
    exec(size = 12, quorum = 7, optimize = false, hazardous = false)
  }

  test("cluster of 12,  7 o -") {
    exec(size = 12, quorum = 7, optimize = true, hazardous = false)
  }

  test("cluster of 12,  8 o h") {
    exec(size = 12, quorum = 8, optimize = true, hazardous = true)
  }

  test("cluster of 12,  8 - h") {
    exec(size = 12, quorum = 8, optimize = false, hazardous = true)
  }

  test("cluster of 12,  8 - -") {
    exec(size = 12, quorum = 8, optimize = false, hazardous = false)
  }

  test("cluster of 12,  8 o -") {
    exec(size = 12, quorum = 8, optimize = true, hazardous = false)
  }

  test("cluster of 12,  9 o h") {
    exec(size = 12, quorum = 9, optimize = true, hazardous = true)
  }

  test("cluster of 12,  9 - h") {
    exec(size = 12, quorum = 9, optimize = false, hazardous = true)
  }

  test("cluster of 12,  9 - -") {
    exec(size = 12, quorum = 9, optimize = false, hazardous = false)
  }

  test("cluster of 12,  9 o -") {
    exec(size = 12, quorum = 9, optimize = true, hazardous = false)
  }

  test("cluster of 12, 10 o h") {
    exec(size = 12, quorum = 10, optimize = true, hazardous = true)
  }

  test("cluster of 12, 10 - h") {
    exec(size = 12, quorum = 10, optimize = false, hazardous = true)
  }

  test("cluster of 12, 10 - -") {
    exec(size = 12, quorum = 10, optimize = false, hazardous = false)
  }

  test("cluster of 12, 10 o -") {
    exec(size = 12, quorum = 10, optimize = true, hazardous = false)
  }

  test("cluster of 12, 11 o h") {
    exec(size = 12, quorum = 11, optimize = true, hazardous = true)
  }

  ignore("cluster of 12, 11 - h") {
    exec(size = 12, quorum = 11, optimize = false, hazardous = true)
  }

  test("cluster of 12, 11 - -") {
    exec(size = 12, quorum = 11, optimize = false, hazardous = false)
  }

  test("cluster of 12, 11 o -") {
    exec(size = 12, quorum = 11, optimize = true, hazardous = false)
  }

}
