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

package org.terracotta.angela.client;

import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;
import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.client.util.IgniteClientHelper;
import org.terracotta.angela.common.ClusterToolExecutionResult;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.topology.InstanceId;

public class ClusterTool {
  private final TerracottaServer terracottaServer;
  private final int ignitePort;
  private final TerracottaCommandLineEnvironment tcEnv;
  private final Ignite ignite;
  private final InstanceId instanceId;

  ClusterTool(Ignite ignite, InstanceId instanceId, TerracottaServer terracottaServer, int ignitePort, TerracottaCommandLineEnvironment tcEnv) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.terracottaServer = terracottaServer;
    this.ignitePort = ignitePort;
    this.tcEnv = tcEnv;
  }

  public ClusterToolExecutionResult executeCommand(String... arguments) {
    IgniteCallable<ClusterToolExecutionResult> callable = () -> Agent.controller.clusterTool(instanceId, terracottaServer, tcEnv, arguments);
    return IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), ignitePort, callable);
  }

}
