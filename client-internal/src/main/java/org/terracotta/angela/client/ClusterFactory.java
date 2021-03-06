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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.client.config.ClientArrayConfigurationContext;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.client.config.MonitoringConfigurationContext;
import org.terracotta.angela.client.config.TmsConfigurationContext;
import org.terracotta.angela.client.config.ToolConfigurationContext;
import org.terracotta.angela.client.config.TsaConfigurationContext;
import org.terracotta.angela.client.config.VoterConfigurationContext;
import org.terracotta.angela.client.remote.agent.RemoteAgentLauncher;
import org.terracotta.angela.common.cluster.Cluster;
import org.terracotta.angela.common.metrics.HardwareMetric;
import org.terracotta.angela.common.metrics.MonitoringCommand;
import org.terracotta.angela.common.net.DefaultPortAllocator;
import org.terracotta.angela.common.topology.InstanceId;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.terracotta.angela.common.util.IpUtils.isLocal;

public class ClusterFactory implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(ClusterFactory.class);

  private static final String TSA = "tsa";
  private static final String TMS = "tms";
  private static final String CLIENT_ARRAY = "clientArray";
  private static final String MONITOR = "monitor";
  private static final String CLUSTER_TOOL = "clusterTool";
  private static final String CONFIG_TOOL = "configTool";
  private static final String VOTER = "voter";
  private static final DateTimeFormatter PATH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmssSSS");

  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final String idPrefix;
  private final AtomicInteger instanceIndex;
  private final Map<String, String> agentsInstance = new HashMap<>();
  private final ConfigurationContext configurationContext;

  private transient RemoteAgentLauncher remoteAgentLauncher;
  private InstanceId monitorInstanceId;

  private final ClusterAgent localAgent;

  public ClusterFactory(ClusterAgent agent, String idPrefix, ConfigurationContext configurationContext) {
    // Using UTC to have consistent layout even in case of timezone skew between client and server.
    this.idPrefix = idPrefix + "-" + LocalDateTime.now(ZoneId.of("UTC")).format(PATH_FORMAT);
    this.instanceIndex = new AtomicInteger();
    this.configurationContext = configurationContext;
    this.remoteAgentLauncher = configurationContext.remoting().buildRemoteAgentLauncher();
    this.localAgent = agent;
    agentsInstance.put("localhost", "localhost:" + localAgent.getIgniteDiscoveryPort());
  }

  private InstanceId init(String type, Collection<String> hostnames) {
    if (hostnames.isEmpty()) {
      throw new IllegalArgumentException("Cannot initialize with 0 server");
    }
    InstanceId instanceId = new InstanceId(idPrefix + "-" + instanceIndex.getAndIncrement(), type);
    for (String hostname : hostnames) {
      if (hostname == null) {
        throw new IllegalArgumentException("Cannot initialize with a null server name");
      }

      if (!isLocal(hostname) && !agentsInstance.containsKey(hostname)) {
        final String nodeName = hostname + ":" + localAgent.getIgniteDiscoveryPort();

        StringBuilder addressesToDiscover = new StringBuilder();
        for (String agentAddress : agentsInstance.values()) {
          addressesToDiscover.append(agentAddress).append(",");
        }
        if (addressesToDiscover.length() > 0) {
          addressesToDiscover.deleteCharAt(addressesToDiscover.length() - 1);
        }
        remoteAgentLauncher.remoteStartAgentOn(hostname, nodeName, localAgent.getIgniteDiscoveryPort(), localAgent.getIgniteComPort(), addressesToDiscover.toString());
        // start remote agent
        agentsInstance.put(hostname, nodeName);
      }
    }

    logger.info("Agents instance (size = {}) : ", agentsInstance.values().size());
    agentsInstance.values().forEach(value -> logger.info("- agent instance : {}", value));

    return instanceId;
  }

  public Cluster cluster() {
    if (localAgent.getIgnite() == null) {
      throw new IllegalStateException("No cluster component started");
    }
    return new Cluster(localAgent.getIgnite());
  }

  public Tsa tsa() {
    TsaConfigurationContext tsaConfigurationContext = configurationContext.tsa();
    if (tsaConfigurationContext == null) {
      throw new IllegalArgumentException("tsa() configuration missing in the ConfigurationContext");
    }
    InstanceId instanceId = init(TSA, tsaConfigurationContext.getTopology().getServersHostnames());

    Tsa tsa = new Tsa(localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(), localAgent.getPortAllocator(), instanceId, tsaConfigurationContext);
    controllers.add(tsa);
    return tsa;
  }

  public Tms tms() {
    TmsConfigurationContext tmsConfigurationContext = configurationContext.tms();
    if (tmsConfigurationContext == null) {
      throw new IllegalArgumentException("tms() configuration missing in the ConfigurationContext");
    }
    InstanceId instanceId = init(TMS, Collections.singletonList(tmsConfigurationContext.getHostname()));

    Tms tms = new Tms(localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(), instanceId, tmsConfigurationContext);
    controllers.add(tms);
    return tms;
  }

  public ClusterTool clusterTool() {
    ToolConfigurationContext clusterToolConfigurationContext = configurationContext.clusterTool();
    if (clusterToolConfigurationContext == null) {
      throw new IllegalArgumentException("clusterTool() configuration missing in the ConfigurationContext");
    }
    InstanceId instanceId = init(CLUSTER_TOOL, Collections.singleton(clusterToolConfigurationContext.getHostName()));
    Tsa tsa = controllers.stream()
        .filter(controller -> controller instanceof Tsa)
        .map(autoCloseable -> (Tsa)autoCloseable)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Tsa should be defined before cluster tool in ConfigurationContext"));
    ClusterTool clusterTool = new ClusterTool(localAgent.getIgnite(), instanceId, localAgent.getIgniteDiscoveryPort(), clusterToolConfigurationContext, tsa);
    controllers.add(clusterTool);
    return clusterTool;
  }

  public ConfigTool configTool() {
    ToolConfigurationContext configToolConfigurationContext = configurationContext.configTool();
    if (configToolConfigurationContext == null) {
      throw new IllegalArgumentException("configTool() configuration missing in the ConfigurationContext");
    }
    InstanceId instanceId = init(CONFIG_TOOL, Collections.singleton(configToolConfigurationContext.getHostName()));
    Tsa tsa = controllers.stream()
        .filter(controller -> controller instanceof Tsa)
        .map(autoCloseable -> (Tsa)autoCloseable)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Tsa should be defined before config tool in ConfigurationContext"));
    ConfigTool configTool = new ConfigTool(localAgent.getIgnite(), instanceId, localAgent.getIgniteDiscoveryPort(), configToolConfigurationContext, tsa);
    controllers.add(configTool);
    return configTool;
  }

  public Voter voter() {
    VoterConfigurationContext voterConfigurationContext = configurationContext.voter();
    if (voterConfigurationContext == null) {
      throw new IllegalArgumentException("voter() configuration missing in the ConfigurationContext");
    }
    InstanceId instanceId = init(VOTER, voterConfigurationContext.getHostNames());
    Voter voter = new Voter(localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(), instanceId, voterConfigurationContext);
    controllers.add(voter);
    return voter;
  }

  public ClientArray clientArray() {
    ClientArrayConfigurationContext clientArrayConfigurationContext = configurationContext.clientArray();
    if (clientArrayConfigurationContext == null) {
      throw new IllegalArgumentException("clientArray() configuration missing in the ConfigurationContext");
    }
    init(CLIENT_ARRAY, clientArrayConfigurationContext.getClientArrayTopology().getClientHostnames());

    ClientArray clientArray = new ClientArray(localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(),
        () -> init(CLIENT_ARRAY, clientArrayConfigurationContext.getClientArrayTopology()
            .getClientHostnames()), clientArrayConfigurationContext);
    controllers.add(clientArray);
    return clientArray;
  }

  public ClusterMonitor monitor() {
    MonitoringConfigurationContext monitoringConfigurationContext = configurationContext.monitoring();
    if (monitoringConfigurationContext == null) {
      throw new IllegalArgumentException("monitoring() configuration missing in the ConfigurationContext");
    }
    Map<HardwareMetric, MonitoringCommand> commands = monitoringConfigurationContext.commands();
    Set<String> hostnames = configurationContext.allHostnames();

    if (monitorInstanceId == null) {
      monitorInstanceId = init(MONITOR, hostnames);
      ClusterMonitor clusterMonitor = new ClusterMonitor(this.localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(), monitorInstanceId, hostnames, commands);
      controllers.add(clusterMonitor);
      return clusterMonitor;
    } else {
      return new ClusterMonitor(localAgent.getIgnite(), localAgent.getIgniteDiscoveryPort(), monitorInstanceId, hostnames, commands);
    }
  }

  @Override
  public void close() throws IOException {
      List<Exception> exceptions = new ArrayList<>();

      for (AutoCloseable controller : controllers) {
        try {
          controller.close();
        } catch (Exception e) {
          e.printStackTrace();
          exceptions.add(e);
        }
      }
      controllers.clear();

      monitorInstanceId = null;

      try {
        remoteAgentLauncher.close();
      } catch (Exception e) {
        e.printStackTrace();
        exceptions.add(e);
      }
      remoteAgentLauncher = null;

      if (!exceptions.isEmpty()) {
        IOException ioException = new IOException("Error while closing down Cluster Factory prefixed with " + idPrefix);
        exceptions.forEach(ioException::addSuppressed);
        throw ioException;
      }
  }
}
