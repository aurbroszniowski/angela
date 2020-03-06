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

import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.common.topology.InstanceId;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class IgniteCommonHelper {

  private final static Logger logger = LoggerFactory.getLogger(IgniteCommonHelper.class);

  public static BlockingQueue<Object> fileTransferQueue(Ignite ignite, InstanceId instanceId) {
    return ignite.queue(instanceId + "@file-transfer-queue", 100, new CollectionConfiguration());
  }

  public static void checkForDuplicateAgent(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<Object>> future = ignite.compute(location).broadcastAsync((IgniteCallable<Object>) () -> 0);
    Collection<Object> results = future.get(60, TimeUnit.SECONDS);
    if (results.size() != 1) {
      throw new IllegalStateException("Node with name [" + nodeName + "] already exists on the network, refusing to duplicate it.");
    }
  }

  public static void displayCluster(Ignite ignite) {
    logger.info("Nodes of the ignite cluster (size = {}) : ", ignite.cluster().nodes().size());
    ignite.cluster().nodes()
        .forEach(node -> logger.info("- Nodename : {}", Objects.requireNonNull(node.attribute("nodename")).toString()));
  }
}
