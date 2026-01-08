/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
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
package org.terracotta.angela.agent.com.grid;

public interface GridCluster {
  GridBarrier barrier(String name, int parties);

  GridAtomicCounter atomicCounter(String name, long initialValue);

  GridAtomicBoolean atomicBoolean(String name, boolean initialValue);

  <T> GridAtomicReference<T> atomicReference(String name, T initialValue);

  /**
   * @return the logical node name of the local grid member executing the current job.
   */
  String getLocalNodeName();
}
