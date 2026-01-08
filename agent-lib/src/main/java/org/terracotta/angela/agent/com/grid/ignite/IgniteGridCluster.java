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
package org.terracotta.angela.agent.com.grid.ignite;

import org.apache.ignite.Ignite;
import org.terracotta.angela.agent.com.grid.GridAtomicBoolean;
import org.terracotta.angela.agent.com.grid.GridAtomicCounter;
import org.terracotta.angela.agent.com.grid.GridAtomicReference;
import org.terracotta.angela.agent.com.grid.GridBarrier;
import org.terracotta.angela.agent.com.grid.GridCluster;

public class IgniteGridCluster implements GridCluster {
  private final Ignite ignite;

  public IgniteGridCluster(Ignite ignite) {
    this.ignite = ignite;
  }

  @Override
  public GridBarrier barrier(String name, int parties) {
    return new IgniteGridBarrier(ignite, parties, name);
  }

  @Override
  public GridAtomicCounter atomicCounter(String name, long initialValue) {
    return new IgniteGridAtomicCounter(ignite, name, initialValue);
  }

  @Override
  public GridAtomicBoolean atomicBoolean(String name, boolean initialValue) {
    return new IgniteGridAtomicBoolean(ignite, name, initialValue);
  }

  @Override
  public <T> GridAtomicReference<T> atomicReference(String name, T initialValue) {
    return new IgniteGridAtomicReference<>(ignite, name, initialValue);
  }

  @Override
  public String getLocalNodeName() {
    Object attribute = ignite.cluster().localNode().attribute("angela.nodeName");
    return attribute == null ? null : attribute.toString();
  }
}
