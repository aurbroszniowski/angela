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
package org.terracotta.angela;

import org.junit.Test;
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.ConfigTool;
import org.terracotta.angela.client.Tsa;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.common.ToolExecutionResult;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.topology.Topology;
import org.terracotta.angela.util.Versions;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.terracotta.angela.client.config.custom.CustomConfigurationContext.customConfigurationContext;
import static org.terracotta.angela.client.support.hamcrest.AngelaMatchers.successful;
import static org.terracotta.angela.common.TerracottaConfigTool.configTool;
import static org.terracotta.angela.common.distribution.Distribution.distribution;
import static org.terracotta.angela.common.dynamic_cluster.Stripe.stripe;
import static org.terracotta.angela.common.provider.DynamicConfigManager.dynamicCluster;
import static org.terracotta.angela.common.tcconfig.TerracottaServer.server;
import static org.terracotta.angela.common.topology.LicenseType.TERRACOTTA_OS;
import static org.terracotta.angela.common.topology.PackageType.KIT;
import static org.terracotta.angela.common.topology.Version.version;

public class ConfigToolIT extends BaseIT {

  public ConfigToolIT(String mode, String hostname, boolean inline, boolean ssh) {
    super(mode, hostname, inline, ssh);
  }

  @Test
  public void testFailingConfigToolCommand() throws Exception {
    TerracottaServer server = server("server-1", hostname)
        .configRepo("terracotta1/repository")
        .logs("terracotta1/logs")
        .metaData("terracotta1/metadata")
        .failoverPriority("availability");
    Distribution distribution = distribution(version(Versions.EHCACHE_VERSION_DC), KIT, TERRACOTTA_OS);
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(context -> context.topology(new Topology(distribution, dynamicCluster(stripe(server)))))
        .configTool(context -> context.configTool(configTool("config-tool", hostname)).distribution(distribution));

    try (ClusterFactory factory = angelaOrchestrator.newClusterFactory("ConfigToolTest::testFailingClusterToolCommand", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.spawnAll();
      ConfigTool configTool = factory.configTool();

      ToolExecutionResult result = configTool.executeCommand("non-existent-command");
      assertThat(result, is(not(successful())));
    }
  }

  @Test
  public void testValidConfigToolCommand() throws Exception {
    TerracottaServer server = server("server-1", hostname)
        .configRepo("terracotta1/repository")
        .logs("terracotta1/logs")
        .metaData("terracotta1/metadata")
        .failoverPriority("availability");
    Distribution distribution = distribution(version(Versions.EHCACHE_VERSION_DC), KIT, TERRACOTTA_OS);
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(context -> context.topology(new Topology(distribution, dynamicCluster(stripe(server)))))
        .configTool(context -> context.configTool(configTool("config-tool", hostname)).distribution(distribution));

    try (ClusterFactory factory = angelaOrchestrator.newClusterFactory("ConfigToolTest::testValidConfigToolCommand", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.spawnAll();
      ConfigTool configTool = factory.configTool();

      final int aServerPort = tsa.getTsaConfigurationContext().getTopology().getServers().iterator().next().getTsaPort();
      ToolExecutionResult result = configTool.executeCommand("get", "-s", hostname + ":" + aServerPort, "-c", "offheap-resources");
      System.out.println(result);
    }
  }
}