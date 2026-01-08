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
package org.terracotta.angela.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.agent.AgentController;
import org.terracotta.angela.agent.com.AgentID;
import org.terracotta.angela.agent.com.Executor;
import org.terracotta.angela.agent.kit.LocalKitManager;
import org.terracotta.angela.client.config.VoterConfigurationContext;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.TerracottaVoter;
import org.terracotta.angela.common.TerracottaVoterState;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.net.PortAllocator;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.tcconfig.SecurityRootDirectory;
import org.terracotta.angela.common.topology.InstanceId;
import org.terracotta.angela.agent.com.grid.RemoteCallable;
import org.terracotta.angela.agent.com.grid.RemoteRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.terracotta.angela.common.AngelaProperties.KIT_COPY;
import static org.terracotta.angela.common.AngelaProperties.KIT_INSTALLATION_DIR;
import static org.terracotta.angela.common.AngelaProperties.KIT_INSTALLATION_PATH;
import static org.terracotta.angela.common.AngelaProperties.OFFLINE;
import static org.terracotta.angela.common.AngelaProperties.SKIP_UNINSTALL;
import static org.terracotta.angela.common.AngelaProperties.getEitherOf;

public class Voter implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Voter.class);

  private final transient Executor executor;
  private final InstanceId instanceId;
  private final transient VoterConfigurationContext voterConfigurationContext;
  private final transient LocalKitManager localKitManager;
  private boolean closed = false;

  Voter(Executor executor, PortAllocator portAllocator, InstanceId instanceId, VoterConfigurationContext voterConfigurationContext) {
    this.voterConfigurationContext = voterConfigurationContext;
    this.instanceId = instanceId;
    this.executor = executor;
    this.localKitManager = new LocalKitManager(portAllocator, voterConfigurationContext.getDistribution());
    installAll();
  }

  private void installAll() {
    List<TerracottaVoter> terracottaVoters = voterConfigurationContext.getTerracottaVoters();
    for (TerracottaVoter terracottaVoter : terracottaVoters) {
      install(terracottaVoter);
    }
  }

  public TerracottaVoterState getTerracottaVoterState(TerracottaVoter terracottaVoter) {
    final AgentID agentID = executor.getAgentID(terracottaVoter.getHostName());
    return executor.execute(agentID, () -> AgentController.getInstance().getVoterState(instanceId, terracottaVoter));
  }

  public Voter startAll() {
    voterConfigurationContext.getTerracottaVoters().stream()
        .map(voter -> CompletableFuture.runAsync(() -> start(voter)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public Voter start(TerracottaVoter terracottaVoter) {
    return start(terracottaVoter, Collections.emptyMap());
  }

  public Voter start(TerracottaVoter terracottaVoter, Map<String, String> envOverrides) {
    final AgentID agentID = executor.getAgentID(terracottaVoter.getHostName());
    logger.info("Starting voter: {} on: {}", instanceId, agentID);
    executor.execute(agentID, () -> AgentController.getInstance().startVoter(instanceId, terracottaVoter, envOverrides));
    return this;
  }

  public Voter stopAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (TerracottaVoter terracottaVoter : voterConfigurationContext.getTerracottaVoters()) {
      try {
        stop(terracottaVoter);
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error stopping all voters");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public Voter stop(TerracottaVoter terracottaVoter) {
    TerracottaVoterState terracottaVoterState = getTerracottaVoterState(terracottaVoter);
    if (terracottaVoterState == TerracottaVoterState.STOPPED) {
      return this;
    }
    final AgentID agentID = executor.getAgentID(terracottaVoter.getHostName());
    logger.info("Stopping Voter: {} on: {}", instanceId, agentID);
    executor.execute(agentID, () -> AgentController.getInstance().stopVoter(instanceId, terracottaVoter));
    return this;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    stopAll();
    if (!SKIP_UNINSTALL.getBooleanValue()) {
      uninstallAll();
    }
  }

  private void install(TerracottaVoter terracottaVoter) {
    installWithKitManager(terracottaVoter);
  }

  private void installWithKitManager(TerracottaVoter terracottaVoter) {
    TerracottaVoterState terracottaVoterState = getTerracottaVoterState(terracottaVoter);
    if (terracottaVoterState != TerracottaVoterState.NOT_INSTALLED) {
      throw new IllegalStateException("Cannot install: voter " + terracottaVoter.getId() + " in state " + terracottaVoterState);
    }

    Distribution distribution = voterConfigurationContext.getDistribution();
    License license = voterConfigurationContext.getLicense();
    TerracottaCommandLineEnvironment tcEnv = voterConfigurationContext.commandLineEnv();
    SecurityRootDirectory securityRootDirectory = voterConfigurationContext.getSecurityRootDirectory();
    String kitInstallationPath = getEitherOf(KIT_INSTALLATION_DIR, KIT_INSTALLATION_PATH);
    localKitManager.setupLocalInstall(license, kitInstallationPath, OFFLINE.getBooleanValue(), tcEnv);
    final AgentID agentID = executor.getAgentID(terracottaVoter.getHostName());
    final String kitInstallationName = localKitManager.getKitInstallationName();

    RemoteCallable<Boolean> callable = () -> AgentController.getInstance().installVoter(instanceId, terracottaVoter, distribution, license, kitInstallationName, securityRootDirectory, tcEnv, kitInstallationPath);

    logger.info("Installing Voter: {} on: {}", instanceId, agentID);

    boolean isRemoteInstallationSuccessful = executor.execute(agentID, callable);
    if (!isRemoteInstallationSuccessful && (kitInstallationPath == null || !KIT_COPY.getBooleanValue())) {
      try {
        executor.uploadKit(agentID, instanceId, distribution, kitInstallationName, localKitManager.getKitInstallationPath());
        executor.execute(agentID, callable);
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + terracottaVoter.getHostName(), e);
      }
    }
  }

  private void uninstallAll() {
    for (TerracottaVoter terracottaVoter : voterConfigurationContext.getTerracottaVoters()) {
      uninstall(terracottaVoter);
    }
  }

  private void uninstall(TerracottaVoter terracottaVoter) {
    TerracottaVoterState terracottaVoterState = getTerracottaVoterState(terracottaVoter);
    if (terracottaVoterState == null) {
      return;
    }
    if (terracottaVoterState != TerracottaVoterState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: voter " + terracottaVoter.getId() + " in state " + terracottaVoterState);
    }
    final AgentID agentID = executor.getAgentID(terracottaVoter.getHostName());
    final Distribution distribution = voterConfigurationContext.getDistribution();
    final String kitInstallationName = localKitManager.getKitInstallationName();

    logger.info("Uninstalling Voter: {} from: {}", instanceId, agentID);

    RemoteRunnable uninstaller = () -> AgentController.getInstance().uninstallVoter(instanceId, distribution, terracottaVoter, kitInstallationName);
    executor.execute(agentID, uninstaller);
  }
}
