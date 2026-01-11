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
package org.terracotta.angela.agent.com.grid;

import org.apache.ignite.internal.util.lang.GridPeerDeployAware;
import org.apache.ignite.lang.IgniteRunnable;
import org.terracotta.angela.agent.com.Exceptions;

import java.io.Serializable;

/**
 * Ignite-friendly wrapper that executes a {@link RemoteRunnable} on the target node.
 */
public final class GridRemoteRunnableJob implements IgniteRunnable, GridPeerDeployAware, Serializable {
  private static final long serialVersionUID = 1L;

  private final RemoteRunnable delegate;

  public GridRemoteRunnableJob(RemoteRunnable delegate) {
    this.delegate = delegate;
  }

  @Override
  public void run() {
    try {
      delegate.run();
    } catch (Exception e) {
      throw Exceptions.asRuntime(e);
    }
  }

  @Override
  public Class<?> deployClass() {
    return delegate == null ? getClass() : delegate.getClass();
  }

  @Override
  public ClassLoader classLoader() {
    return delegate == null ? getClass().getClassLoader() : delegate.getClass().getClassLoader();
  }
}
