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

import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.agent.AgentController;
import org.terracotta.angela.agent.com.AgentExecutor;
import org.terracotta.angela.agent.com.Executor;
import org.terracotta.angela.agent.kit.LocalKitManager;
import org.terracotta.angela.client.config.ToolConfigurationContext;
import static org.terracotta.angela.common.AngelaProperties.KIT_COPY;
import static org.terracotta.angela.common.AngelaProperties.KIT_INSTALLATION_DIR;
import static org.terracotta.angela.common.AngelaProperties.KIT_INSTALLATION_PATH;
import static org.terracotta.angela.common.AngelaProperties.OFFLINE;
import static org.terracotta.angela.common.AngelaProperties.SKIP_UNINSTALL;
import static org.terracotta.angela.common.AngelaProperties.getEitherOf;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.ToolExecutionResult;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.net.PortAllocator;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.tcconfig.SecurityRootDirectory;
import org.terracotta.angela.common.topology.InstanceId;
import org.terracotta.angela.agent.com.grid.RemoteCallable;
import org.terracotta.angela.agent.com.grid.RemoteRunnable;

/**
 *
 * @author dpra
 */
public class ImportTool implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(ImportTool.class);

  private final InstanceId instanceId;
  private final transient AgentExecutor executor;
  private final transient ToolConfigurationContext configContext;
  private final transient LocalKitManager localKitManager;
  private final transient Tsa tsa;

  ImportTool(Executor executor, PortAllocator portAllocator, InstanceId instanceId, ToolConfigurationContext configContext, Tsa tsa) {
    this.instanceId = instanceId;
    this.executor = executor.forAgent(executor.getAgentID(configContext.getHostName()));
    this.configContext = configContext;
    this.localKitManager = new LocalKitManager(portAllocator, configContext.getDistribution());
    this.tsa = tsa;
    install();
  }

  public ToolExecutionResult executeCommand(String... arguments) {
    return executeCommand(Collections.emptyMap(), arguments);
  }

  public ToolExecutionResult executeCommand(Map<String, String> env, String... command) {
    logger.debug("Executing import-tool: {} on: {}", instanceId, executor.getTarget());
    return executor.execute(() -> AgentController.getInstance().importTool(instanceId, env, command));
  }

  public ImportTool install() {
    Distribution distribution = configContext.getDistribution();
    License license = tsa.getTsaConfigurationContext().getLicense();
    TerracottaCommandLineEnvironment tcEnv = configContext.getCommandLineEnv();
    SecurityRootDirectory securityRootDirectory = configContext.getSecurityRootDirectory();

    String kitInstallationPath = getEitherOf(KIT_INSTALLATION_DIR, KIT_INSTALLATION_PATH);
    localKitManager.setupLocalInstall(license, kitInstallationPath, OFFLINE.getBooleanValue(), tcEnv);
    final String hostName = configContext.getHostName();
    final String kitInstallationName = localKitManager.getKitInstallationName();

    logger.info("Installing import-tool: {} on: {}", instanceId, executor.getTarget());

    RemoteCallable<Boolean> callable = () -> AgentController.getInstance().installImportTool(instanceId, hostName, distribution, license, kitInstallationName, securityRootDirectory, tcEnv, kitInstallationPath);
    boolean isRemoteInstallationSuccessful = executor.execute(callable);
    if (!isRemoteInstallationSuccessful && (kitInstallationPath == null || !KIT_COPY.getBooleanValue())) {
      try {
        executor.uploadKit(instanceId, distribution, kitInstallationName, localKitManager.getKitInstallationPath());
        executor.execute(callable);
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + hostName, e);
      }
    }
    return this;
  }

  public ImportTool uninstall() {
    logger.info("Uninstalling import-tool: {} on: {}", instanceId, executor.getTarget());
    final Distribution distribution = configContext.getDistribution();
    final String hostName = configContext.getHostName();
    final String kitInstallationName = localKitManager.getKitInstallationName();
    RemoteRunnable uninstaller = () -> AgentController.getInstance().uninstallImportTool(instanceId, distribution, hostName, kitInstallationName);
    executor.execute(uninstaller);
    return this;
  }

  @Override
  public void close() {
    if (!SKIP_UNINSTALL.getBooleanValue()) {
      uninstall();
    }
  }

}
