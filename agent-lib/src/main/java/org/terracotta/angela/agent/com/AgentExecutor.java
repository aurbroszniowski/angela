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

import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.topology.InstanceId;
import org.terracotta.angela.agent.com.grid.RemoteCallable;
import org.terracotta.angela.agent.com.grid.RemoteRunnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Mathieu Carbou
 */
public class AgentExecutor {
  private final Executor executor;
  private final AgentID agentID;

  public AgentExecutor(Executor executor, AgentID agentID) {
    this.executor = executor;
    this.agentID = agentID;
  }

  public AgentID getTarget() {
    return agentID;
  }

  public Executor getExecutor() {
    return executor;
  }

  // for target

  public void execute(RemoteRunnable job) {
    executor.execute(agentID, job);
  }

  public Future<Void> executeAsync(RemoteRunnable job) {
    return executor.executeAsync(agentID, job);
  }

  public <R> R execute(RemoteCallable<R> job) {return executor.execute(agentID, job);}

  public <R> Future<R> executeAsync(RemoteCallable<R> job) {return executor.executeAsync(agentID, job);}

  public void uploadKit(InstanceId instanceId, Distribution distribution, String kitInstallationName, Path kitInstallationPath) throws IOException, InterruptedException {
    executor.uploadKit(agentID, instanceId, distribution, kitInstallationName, kitInstallationPath);
  }

  public void uploadClientJars(InstanceId instanceId, List<Path> locations) throws IOException, InterruptedException {executor.uploadClientJars(agentID, instanceId, locations);}
}
