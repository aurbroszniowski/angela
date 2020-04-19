/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */
package org.terracotta.angela.common.util;

import java.util.concurrent.TimeUnit;

public class TimeBudget {
  private final long budgetExpiry;
  private final TimeUnit timeUnit;

  public TimeBudget(long timeout, TimeUnit timeUnit) {
    this.timeUnit = timeUnit;
    this.budgetExpiry = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeout, timeUnit);
  }

  public long remaining() {
    return remaining(timeUnit);
  }

  public long remaining(TimeUnit timeUnit) {
    long now = System.nanoTime();
    long remaining = budgetExpiry - now;
    return timeUnit.convert(remaining, TimeUnit.NANOSECONDS);
  }

  @Override
  public String toString() {
    return "TimeBudget{" +
        "budgetExpiry=" + budgetExpiry +
        ", remaining=" + remaining() +
        ", timeUnit=" + timeUnit +
        '}';
  }
}
