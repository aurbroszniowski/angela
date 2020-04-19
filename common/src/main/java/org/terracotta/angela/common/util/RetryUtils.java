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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class RetryUtils {
  private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

  public static boolean waitFor(Callable<Boolean> condition, long maxWaitTimeMillis) {
    return waitFor(condition, maxWaitTimeMillis, MILLISECONDS);
  }

  /**
   * A general-purpose utility for source code, intended as a replacement for Awaitility.
   * Repeatedly polls the {@code condition} with a backoff between multiple poll events, until the specified duration
   * is reached.
   *
   * @param condition       the condition to evaluate
   * @param maxWaitDuration the maximum duration to wait before giving up
   * @param timeUnit        the unit of duration
   * @return {@code true} if the condition was evaluated to true within the given constraints, false otherwise
   */
  public static boolean waitFor(Callable<Boolean> condition, long maxWaitDuration, TimeUnit timeUnit) {
    TimeBudget timeBudget = new TimeBudget(maxWaitDuration, timeUnit);
    long currRetryTime = min(maxWaitDuration, 100);
    ExecutorService executorService = Executors.newSingleThreadExecutor(Thread::new);
    boolean success = false;

    while (timeBudget.remaining() > 0 && !Thread.currentThread().isInterrupted()) {
      long timeout = min(currRetryTime, timeBudget.remaining()); //Keep track of how much time remains
      Future<Boolean> future = executorService.submit(condition);
      if (getResult(timeout, future)) {
        logger.info("Condition became true after {} {}", maxWaitDuration - timeBudget.remaining(), timeUnit.name().toLowerCase());
        success = true;
        break;
      } else {
        logger.debug("Callable failed. Retrying..");
        sleep(currRetryTime);
        currRetryTime *= 2; //Double retry timeout on each failure
      }
    }

    cleanup(executorService);
    return success;
  }

  private static void sleep(long currRetryTime) {
    try {
      Thread.sleep(currRetryTime);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static Boolean getResult(long timeout, Future<Boolean> future) {
    try {
      return future.get(timeout, MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }

  private static void cleanup(ExecutorService executorService) {
    try {
      executorService.shutdownNow();
      executorService.awaitTermination(1, MILLISECONDS);
    } catch (InterruptedException e) {
      // Ignore
    }
  }
}
