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
import org.apache.ignite.IgniteAtomicLong;
import org.terracotta.angela.agent.com.grid.GridAtomicBoolean;

class IgniteGridAtomicBoolean implements GridAtomicBoolean {
  private final IgniteAtomicLong igniteCounter;

  IgniteGridAtomicBoolean(Ignite ignite, String name, boolean initVal) {
    this.igniteCounter = ignite.atomicLong("Atomic-Boolean-" + name, initVal ? 1L : 0L, true);
  }

  @Override
  public boolean get() {
    return igniteCounter.get() != 0L;
  }

  @Override
  public void set(boolean value) {
    igniteCounter.getAndSet(value ? 1L : 0L);
  }

  @Override
  public boolean getAndSet(boolean value) {
    return igniteCounter.getAndSet(value ? 1L : 0L) != 0L;
  }

  @Override
  public boolean compareAndSet(boolean expect, boolean update) {
    return igniteCounter.compareAndSet(expect ? 1L : 0L, update ? 1L : 0L);
  }
}
