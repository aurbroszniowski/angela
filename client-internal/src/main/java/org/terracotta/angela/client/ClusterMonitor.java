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
import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.client.filesystem.RemoteFolder;
import org.terracotta.angela.client.filesystem.TransportableFile;
import org.terracotta.angela.client.util.IgniteClientHelper;
import org.terracotta.angela.common.metrics.HardwareMetric;
import org.terracotta.angela.common.metrics.HardwareMetricsCollector;
import org.terracotta.angela.common.metrics.MonitoringCommand;
import org.terracotta.angela.common.topology.InstanceId;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterMonitor implements AutoCloseable {

  private final Ignite ignite;
  private final int ignitePort;
  private final Path workingPath;
  private final Set<String> hostnames;
  private final Map<HardwareMetric, MonitoringCommand> commands;
  private boolean closed = false;

  ClusterMonitor(Ignite ignite, int ignitePort, InstanceId instanceId, Set<String> hostnames, Map<HardwareMetric, MonitoringCommand> commands) {
    this.ignite = ignite;
    this.ignitePort = ignitePort;
    this.workingPath = Agent.WORK_DIR.resolve(instanceId.toString());
    this.hostnames = hostnames;
    this.commands = commands;
  }

  public ClusterMonitor startOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteClientHelper.executeRemotely(ignite, hostname, ignitePort, () -> Agent.controller.startHardwareMonitoring(workingPath
            .toString(), commands));
      } catch (Exception e) {
        exceptions.add(new RuntimeException("Error starting hardware monitoring on " + hostname, e));
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error starting cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public ClusterMonitor stopOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteClientHelper.executeRemotely(ignite, hostname, ignitePort, () -> Agent.controller.stopHardwareMonitoring());
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error stopping cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public void downloadTo(File location) {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        Path metricsPath = workingPath.resolve(HardwareMetricsCollector.METRICS_DIRECTORY);
        new RemoteFolder(ignite, hostname, ignitePort, null, metricsPath.toString()).downloadTo(new File(location, hostname));
      } catch (IOException e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading cluster monitor remote files");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void processMetrics(BiConsumer<String, TransportableFile> processor) {
    List<Exception> exceptions = new ArrayList<>();
    for (String hostname : hostnames) {
      try {
        Path metricsPath = workingPath.resolve(HardwareMetricsCollector.METRICS_DIRECTORY);
        RemoteFolder remoteFolder = new RemoteFolder(ignite, hostname, ignitePort, null, metricsPath.toString());
        remoteFolder.list().forEach(remoteFile -> processor.accept(hostname, remoteFile.toTransportableFile()));
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading cluster monitor remote files");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public boolean isMonitoringRunning(HardwareMetric metric) {
    for (String hostname : hostnames) {
      boolean running = IgniteClientHelper.executeRemotely(ignite, hostname, ignitePort, () -> Agent.controller.isMonitoringRunning(metric));
      if (!running) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    stopOnAll();
  }

}
