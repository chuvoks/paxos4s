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

class InstanceTest extends FunSuite {

  test("cluster of  3,  2 o h") {
    exec(size = 3, quorum = 2, optimize = true, hazardous = true)
  }

  test("cluster of  3,  2 - h") {
    exec(size = 3, quorum = 2, optimize = false, hazardous = true)
  }

  test("cluster of  3,  2 - -") {
    exec(size = 3, quorum = 2, optimize = false, hazardous = false)
  }

  test("cluster of  3,  2 o -") {
    exec(size = 3, quorum = 2, optimize = true, hazardous = false)
  }

  test("cluster of  4,  3 o h") {
    exec(size = 4, quorum = 3, optimize = true, hazardous = true)
  }

  test("cluster of  4,  3 - h") {
    exec(size = 4, quorum = 3, optimize = false, hazardous = true)
  }

  test("cluster of  4,  3 - -") {
    exec(size = 4, quorum = 3, optimize = false, hazardous = false)
  }

  test("cluster of  4,  3 o -") {
    exec(size = 4, quorum = 3, optimize = true, hazardous = false)
  }

  test("cluster of  5,  3 o h") {
    exec(size = 5, quorum = 3, optimize = true, hazardous = true)
  }

  test("cluster of  5,  3 - h") {
    exec(size = 5, quorum = 3, optimize = false, hazardous = true)
  }

  test("cluster of  5,  3 - -") {
    exec(size = 5, quorum = 3, optimize = false, hazardous = false)
  }

  test("cluster of  5,  3 o -") {
    exec(size = 5, quorum = 3, optimize = true, hazardous = false)
  }

  test("cluster of  5,  4 o h") {
    exec(size = 5, quorum = 4, optimize = true, hazardous = true)
  }

  test("cluster of  5,  4 - h") {
    exec(size = 5, quorum = 4, optimize = false, hazardous = true)
  }

  test("cluster of  5,  4 - -") {
    exec(size = 5, quorum = 4, optimize = false, hazardous = false)
  }

  test("cluster of  5,  4 o -") {
    exec(size = 5, quorum = 4, optimize = true, hazardous = false)
  }

  test("cluster of  6,  4 o h") {
    exec(size = 6, quorum = 4, optimize = true, hazardous = true)
  }

  test("cluster of  6,  4 - h") {
    exec(size = 6, quorum = 4, optimize = false, hazardous = true)
  }

  test("cluster of  6,  4 - -") {
    exec(size = 6, quorum = 4, optimize = false, hazardous = false)
  }

  test("cluster of  6,  4 o -") {
    exec(size = 6, quorum = 4, optimize = true, hazardous = false)
  }

  test("cluster of  6,  5 o h") {
    exec(size = 6, quorum = 5, optimize = true, hazardous = true)
  }

  test("cluster of  6,  5 - h") {
    exec(size = 6, quorum = 5, optimize = false, hazardous = true)
  }

  test("cluster of  6,  5 - -") {
    exec(size = 6, quorum = 5, optimize = false, hazardous = false)
  }

  test("cluster of  6,  5 o -") {
    exec(size = 6, quorum = 5, optimize = true, hazardous = false)
  }

  test("cluster of  7,  4 o h") {
    exec(size = 7, quorum = 4, optimize = true, hazardous = true)
  }

  test("cluster of  7,  4 - h") {
    exec(size = 7, quorum = 4, optimize = false, hazardous = true)
  }

  test("cluster of  7,  4 - -") {
    exec(size = 7, quorum = 4, optimize = false, hazardous = false)
  }

  test("cluster of  7,  4 o -") {
    exec(size = 7, quorum = 4, optimize = true, hazardous = false)
  }

  test("cluster of  7,  5 o h") {
    exec(size = 7, quorum = 5, optimize = true, hazardous = true)
  }

  test("cluster of  7,  5 - h") {
    exec(size = 7, quorum = 5, optimize = false, hazardous = true)
  }

  test("cluster of  7,  5 - -") {
    exec(size = 7, quorum = 5, optimize = false, hazardous = false)
  }

  test("cluster of  7,  5 o -") {
    exec(size = 7, quorum = 5, optimize = true, hazardous = false)
  }

  test("cluster of  7,  6 o h") {
    exec(size = 7, quorum = 6, optimize = true, hazardous = true)
  }

  test("cluster of  7,  6 - h") {
    exec(size = 7, quorum = 6, optimize = false, hazardous = true)
  }

  test("cluster of  7,  6 - -") {
    exec(size = 7, quorum = 6, optimize = false, hazardous = false)
  }

  test("cluster of  7,  6 o -") {
    exec(size = 7, quorum = 6, optimize = true, hazardous = false)
  }

  test("cluster of  8,  5 o h") {
    exec(size = 8, quorum = 5, optimize = true, hazardous = true)
  }

  test("cluster of  8,  5 - h") {
    exec(size = 8, quorum = 5, optimize = false, hazardous = true)
  }

  test("cluster of  8,  5 - -") {
    exec(size = 8, quorum = 5, optimize = false, hazardous = false)
  }

  test("cluster of  8,  5 o -") {
    exec(size = 8, quorum = 5, optimize = true, hazardous = false)
  }

  test("cluster of  8,  6 o h") {
    exec(size = 8, quorum = 6, optimize = true, hazardous = true)
  }

  test("cluster of  8,  6 - h") {
    exec(size = 8, quorum = 6, optimize = false, hazardous = true)
  }

  test("cluster of  8,  6 - -") {
    exec(size = 8, quorum = 6, optimize = false, hazardous = false)
  }

  test("cluster of  8,  6 o -") {
    exec(size = 8, quorum = 6, optimize = true, hazardous = false)
  }

  test("cluster of  8,  7 o h") {
    exec(size = 8, quorum = 7, optimize = true, hazardous = true)
  }

  test("cluster of  8,  7 - h") {
    exec(size = 8, quorum = 7, optimize = false, hazardous = true)
  }

  test("cluster of  8,  7 - -") {
    exec(size = 8, quorum = 7, optimize = false, hazardous = false)
  }

  test("cluster of  8,  7 o -") {
    exec(size = 8, quorum = 7, optimize = true, hazardous = false)
  }

  test("cluster of  9,  5 o h") {
    exec(size = 9, quorum = 5, optimize = true, hazardous = true)
  }

  test("cluster of  9,  5 - h") {
    exec(size = 9, quorum = 5, optimize = false, hazardous = true)
  }

  test("cluster of  9,  5 - -") {
    exec(size = 9, quorum = 5, optimize = false, hazardous = false)
  }

  test("cluster of  9,  5 o -") {
    exec(size = 9, quorum = 5, optimize = true, hazardous = false)
  }

  test("cluster of  9,  6 o h") {
    exec(size = 9, quorum = 6, optimize = true, hazardous = true)
  }

  test("cluster of  9,  6 - h") {
    exec(size = 9, quorum = 6, optimize = false, hazardous = true)
  }

  test("cluster of  9,  6 - -") {
    exec(size = 9, quorum = 6, optimize = false, hazardous = false)
  }

  test("cluster of  9,  6 o -") {
    exec(size = 9, quorum = 6, optimize = true, hazardous = false)
  }

  test("cluster of  9,  7 o h") {
    exec(size = 9, quorum = 7, optimize = true, hazardous = true)
  }

  test("cluster of  9,  7 - h") {
    exec(size = 9, quorum = 7, optimize = false, hazardous = true)
  }

  test("cluster of  9,  7 - -") {
    exec(size = 9, quorum = 7, optimize = false, hazardous = false)
  }

  test("cluster of  9,  7 o -") {
    exec(size = 9, quorum = 7, optimize = true, hazardous = false)
  }

  test("cluster of  9,  8 o h") {
    exec(size = 9, quorum = 8, optimize = true, hazardous = true)
  }

  test("cluster of  9,  8 - h") {
    exec(size = 9, quorum = 8, optimize = false, hazardous = true)
  }

  test("cluster of  9,  8 - -") {
    exec(size = 9, quorum = 8, optimize = false, hazardous = false)
  }

  test("cluster of  9,  8 o -") {
    exec(size = 9, quorum = 8, optimize = true, hazardous = false)
  }

  test("cluster of 10,  6 o h") {
    exec(size = 10, quorum = 6, optimize = true, hazardous = true)
  }

  test("cluster of 10,  6 - h") {
    exec(size = 10, quorum = 6, optimize = false, hazardous = true)
  }

  test("cluster of 10,  6 - -") {
    exec(size = 10, quorum = 6, optimize = false, hazardous = false)
  }

  test("cluster of 10,  6 o -") {
    exec(size = 10, quorum = 6, optimize = true, hazardous = false)
  }

  test("cluster of 10,  7 o h") {
    exec(size = 10, quorum = 7, optimize = true, hazardous = true)
  }

  test("cluster of 10,  7 - h") {
    exec(size = 10, quorum = 7, optimize = false, hazardous = true)
  }

  test("cluster of 10,  7 - -") {
    exec(size = 10, quorum = 7, optimize = false, hazardous = false)
  }

  test("cluster of 10,  7 o -") {
    exec(size = 10, quorum = 7, optimize = true, hazardous = false)
  }

  test("cluster of 10,  8 o h") {
    exec(size = 10, quorum = 8, optimize = true, hazardous = true)
  }

  test("cluster of 10,  8 - h") {
    exec(size = 10, quorum = 8, optimize = false, hazardous = true)
  }

  test("cluster of 10,  8 - -") {
    exec(size = 10, quorum = 8, optimize = false, hazardous = false)
  }

  test("cluster of 10,  8 o -") {
    exec(size = 10, quorum = 8, optimize = true, hazardous = false)
  }

  test("cluster of 10,  9 o h") {
    exec(size = 10, quorum = 9, optimize = true, hazardous = true)
  }

  ignore("cluster of 10,  9 - h") {
    exec(size = 10, quorum = 9, optimize = false, hazardous = true)
  }

  test("cluster of 10,  9 - -") {
    exec(size = 10, quorum = 9, optimize = false, hazardous = false)
  }

  test("cluster of 10,  9 o -") {
    exec(size = 10, quorum = 9, optimize = true, hazardous = false)
  }

}
