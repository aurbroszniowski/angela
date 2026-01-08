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
import org.terracotta.angela.agent.com.grid.GridAtomicCounter;

class IgniteGridAtomicCounter implements GridAtomicCounter {
  private final IgniteAtomicLong igniteCounter;

  IgniteGridAtomicCounter(Ignite ignite, String name, long initVal) {
    this.igniteCounter = ignite.atomicLong("Atomic-Counter-" + name, initVal, true);
  }

  @Override
  public long incrementAndGet() {
    return igniteCounter.incrementAndGet();
  }

  @Override
  public long getAndIncrement() {
    return igniteCounter.getAndIncrement();
  }

  @Override
  public long get() {
    return igniteCounter.get();
  }

  @Override
  public long getAndSet(long value) {
    return igniteCounter.getAndSet(value);
  }

  @Override
  public boolean compareAndSet(long expect, long update) {
    return igniteCounter.compareAndSet(expect, update);
  }
}
