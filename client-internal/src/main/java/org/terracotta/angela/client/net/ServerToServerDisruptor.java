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
package org.terracotta.angela.client.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.agent.AgentController;
import org.terracotta.angela.agent.com.AgentID;
import org.terracotta.angela.agent.com.Executor;
import org.terracotta.angela.common.net.Disruptor;
import org.terracotta.angela.common.net.DisruptorState;
import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.topology.InstanceId;
import org.terracotta.angela.common.topology.Topology;
import org.terracotta.angela.agent.com.grid.RemoteRunnable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Disrupt traffic between set of servers.(i.e active and passives)
 */
public class ServerToServerDisruptor implements Disruptor {
  private static final Logger logger = LoggerFactory.getLogger(ServerToServerDisruptor.class);
  //servers to be linked to serve this disruption
  private final Map<ServerSymbolicName, Collection<ServerSymbolicName>> linkedServers;
  private final Executor executor;
  private final InstanceId instanceId;
  private final Topology topology;
  private final Consumer<Disruptor> closeHook;
  private volatile DisruptorState state;

  ServerToServerDisruptor(Executor executor, InstanceId instanceId, Topology topology, Map<ServerSymbolicName, Collection<ServerSymbolicName>> linkedServers, Consumer<Disruptor> closeHook) {
    this.executor = executor;
    this.instanceId = instanceId;
    this.topology = topology;
    this.linkedServers = linkedServers;
    this.closeHook = closeHook;
    this.state = DisruptorState.UNDISRUPTED;
  }


  @Override
  public void disrupt() {
    if (state != DisruptorState.UNDISRUPTED) {
      throw new IllegalStateException("Illegal state before disrupt:" + state);
    }

    logger.info("disrupting {}", this);
    //invoke disruption remotely on each linked servers.
    Map<ServerSymbolicName, TerracottaServer> topologyServers = new HashMap<>();
    for (TerracottaServer svr : topology.getServers()) {
      topologyServers.put(svr.getServerSymbolicName(), svr);
    }
    for (Map.Entry<ServerSymbolicName, Collection<ServerSymbolicName>> entry : linkedServers.entrySet()) {
      TerracottaServer server = topologyServers.get(entry.getKey());
      Collection<TerracottaServer> otherServers = Collections.unmodifiableCollection(entry.getValue()
          .stream()
          .map(topologyServers::get)
          .collect(Collectors.toList()));
      final AgentID agentID = executor.getAgentID(server.getHostName());
      executor.execute(agentID, blockRemotely(instanceId, server, otherServers));
    }

    state = DisruptorState.DISRUPTED;
  }

  @Override
  public void undisrupt() {
    if (state != DisruptorState.DISRUPTED) {
      throw new IllegalStateException("Illegal state before undisrupt:" + state);
    }

    logger.info("undisrupting {}", this);
    Map<ServerSymbolicName, TerracottaServer> topologyServers = new HashMap<>();
    for (TerracottaServer svr : topology.getServers()) {
      topologyServers.put(svr.getServerSymbolicName(), svr);
    }
    for (Map.Entry<ServerSymbolicName, Collection<ServerSymbolicName>> entry : linkedServers.entrySet()) {
      TerracottaServer server = topologyServers.get(entry.getKey());
      Collection<TerracottaServer> otherServers = Collections.unmodifiableCollection(entry.getValue()
          .stream()
          .map(topologyServers::get)
          .collect(Collectors.toList()));
      final AgentID agentID = executor.getAgentID(server.getHostName());
      executor.execute(agentID, undisruptRemotely(instanceId, server, otherServers));
    }
    state = DisruptorState.UNDISRUPTED;
  }


  @Override
  public void close() {
    if (state == DisruptorState.DISRUPTED) {
      undisrupt();
    }
    if (state == DisruptorState.UNDISRUPTED) {
      //remote server links will be closed when servers are stopped.
      closeHook.accept(this);
      state = DisruptorState.CLOSED;
    }
  }

  Map<ServerSymbolicName, Collection<ServerSymbolicName>> getLinkedServers() {
    return linkedServers;
  }

  private static RemoteRunnable blockRemotely(InstanceId instanceId, TerracottaServer server, Collection<TerracottaServer> otherServers) {
    return () -> AgentController.getInstance().disrupt(instanceId, server, otherServers);
  }

  private static RemoteRunnable undisruptRemotely(InstanceId instanceId, TerracottaServer server, Collection<TerracottaServer> otherServers) {
    return () -> AgentController.getInstance().undisrupt(instanceId, server, otherServers);
  }

  @Override
  public String toString() {
    return "ServerToServerDisruptor{" +
        "linkedServers=" + linkedServers.entrySet()
        .stream()
        .map(e -> e.getKey().getSymbolicName() + "->" + e.getValue()
            .stream()
            .map(ServerSymbolicName::getSymbolicName)
            .collect(Collectors.joining(",", "[", "]")))
        .collect(Collectors.joining(",", "{", "}")) +
        '}';
  }

}
