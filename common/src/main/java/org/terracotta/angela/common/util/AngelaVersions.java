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
package org.terracotta.angela.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AngelaVersions {

  public static final AngelaVersions INSTANCE = new AngelaVersions();

  private final Properties properties;

  private AngelaVersions() {
    try {
      try (InputStream in = getClass().getResourceAsStream("/angela/versions.properties")) {
        properties = new Properties();
        properties.load(in);
      }
    } catch (IOException ioe) {
      throw new RuntimeException("Error loading resource file /angela/versions.properties", ioe);
    }
  }

  public String getAngelaVersion() {
    return properties.getProperty("angela.version");
  }

  public boolean isSnapshot() {
    return getAngelaVersion().endsWith("-SNAPSHOT");
  }

}
