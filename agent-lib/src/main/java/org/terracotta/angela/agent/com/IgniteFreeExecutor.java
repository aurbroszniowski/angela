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
package org.terracotta.angela.agent.com;

import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.agent.client.RemoteClientManager;
import org.terracotta.angela.agent.com.grid.RemoteCallable;
import org.terracotta.angela.agent.com.grid.RemoteRunnable;
import org.terracotta.angela.agent.kit.RemoteKitManager;
import org.terracotta.angela.common.clientconfig.ClientId;
import org.terracotta.angela.common.cluster.Cluster;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.topology.InstanceId;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonList;

/**
 * Executor which is not using Ignite and directly execute closures
 */
public class IgniteFreeExecutor implements Executor {

  private final transient Map<String, BlockingQueue<FileTransfer>> queues = new ConcurrentHashMap<>();
  private final AgentGroup agentGroup;

  public IgniteFreeExecutor(Agent agent) {
    this(agent.getGroupId(), agent.getAgentID());
  }

  public IgniteFreeExecutor(UUID group, AgentID agentID) {
    if (!agentID.isLocal()) {
      throw new IllegalArgumentException("Wrong agentID: " + agentID);
    }
    this.agentGroup = new LocalAgentGroup(group, agentID);
  }

  @Override
  public String toString() {
    return getLocalAgentID().toString();
  }

  @Override
  public void close() {
    queues.clear();
  }

  @Override
  public synchronized void uploadClientJars(AgentID agentID, InstanceId instanceId, List<Path> locations) {
    CompletableFuture<Void> finished = new CompletableFuture<>();
    Thread thread = new Thread(() -> {
      downloadFiles(instanceId, new RemoteClientManager(instanceId).getClientClasspathRoot());
      finished.complete(null);
    }, "downloader-" + instanceId);
    thread.start();
    uploadFiles(instanceId, locations, finished);
  }

  @Override
  public void uploadKit(AgentID agentID, InstanceId instanceId, Distribution distribution, String kitInstallationName, Path kitInstallationPath) {
    RemoteKitManager remoteKitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);
    Path installDir = remoteKitManager.getKitInstallationPath().getParent();
    CompletableFuture<Void> finished = new CompletableFuture<>();
    Thread thread = new Thread(() -> {
      downloadFiles(instanceId, installDir);
      finished.complete(null);
    }, "downloader-" + instanceId);
    thread.start();
    uploadFiles(instanceId, singletonList(kitInstallationPath), finished);
  }

  @Override
  public Optional<CompletableFuture<Void>> shutdown(AgentID agentID) {
    return Optional.empty();
  }

  @Override
  public AgentID getLocalAgentID() {
    return agentGroup.getLocalAgentID();
  }

  @Override
  public Optional<AgentID> findAgentID(String hostname) {
    return Optional.of(getLocalAgentID());
  }

  @Override
  public Optional<AgentID> startRemoteAgent(String hostname) {
    return Optional.empty(); // do not spawn new agents
  }

  @Override
  public AgentGroup getGroup() {
    return agentGroup;
  }

  @Override
  public Cluster getCluster() {
    throw new UnsupportedOperationException("Unsupported in local mode");
  }

  @Override
  public Cluster getCluster(ClientId clientId) {
    throw new UnsupportedOperationException("Unsupported in local mode");
  }

  @Override
  public Future<Void> executeAsync(AgentID agentID, RemoteRunnable job) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      job.run();
      future.complete(null);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  @Override
  public <R> Future<R> executeAsync(AgentID agentID, RemoteCallable<R> job) {
    CompletableFuture<R> future = new CompletableFuture<>();
    try {
      future.complete(job.call());
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  @Override
  public BlockingQueue<FileTransfer> getFileTransferQueue(InstanceId instanceId) {
    return queues.computeIfAbsent(instanceId + "@file-transfer-queue", s -> new LinkedBlockingQueue<>(500));
  }
}
