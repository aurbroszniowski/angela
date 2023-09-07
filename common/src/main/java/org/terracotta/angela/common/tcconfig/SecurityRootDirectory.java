/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vmad
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public class SecurityRootDirectory implements Serializable {

  private static final long serialVersionUID = 1L;

  static final String TRUSTED_AUTHORITY_DIR_NAME = "trusted-authority";
  static final String IDENTITY_DIR_NAME = "identity";
  static final String ACCESS_CONTROL_DIR_NAME = "access-control";

  @Deprecated
  public static final String WHITE_LIST_DEPRECATED_DIR_NAME = "whitelist-deprecated";
  @Deprecated
  public static final String WHITE_LIST_DEPRECATED_FILE_NAME = "whitelist-deprecated.txt";

  static final String WHITE_LIST_FILE_NAME = "whitelist.txt";



  private final Map<String, byte[]> identityMap;
  private final Map<String, byte[]> trustedAuthorityMap;
  private final Map<String, byte[]> accessControlMap;
  private final Map<String, byte[]> whiteListDeprecatedMap;
  private final String securityRootDirectory;
  private byte[] whiteListFileContent;

  private SecurityRootDirectory(Path securityRootDirectory) {
    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    if (Files.exists(identityDir)) {
      identityMap = new HashMap<>();
      storeContentsToMap(identityDir, identityMap);
    } else {
      identityMap = null;
    }

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    if (Files.exists(trustedAuthorityDir)) {
      trustedAuthorityMap = new HashMap<>();
      storeContentsToMap(trustedAuthorityDir, trustedAuthorityMap);
    } else {
      trustedAuthorityMap = null;
    }

    Path accessControlDir = securityRootDirectory.resolve(ACCESS_CONTROL_DIR_NAME);
    if (Files.exists(accessControlDir)) {
      accessControlMap = new HashMap<>();
      storeContentsToMap(accessControlDir, accessControlMap);
    } else {
      accessControlMap = null;
    }

    Path whiteListDeprecatedDir = securityRootDirectory.resolve(WHITE_LIST_DEPRECATED_DIR_NAME);
    if (Files.exists(whiteListDeprecatedDir)) {
      whiteListDeprecatedMap = new HashMap<>();
      storeContentsToMap(whiteListDeprecatedDir, whiteListDeprecatedMap);
    } else {
      whiteListDeprecatedMap = null;
    }

    Path whiteListFile = securityRootDirectory.resolve(WHITE_LIST_FILE_NAME);
    if (Files.exists(whiteListFile)) {
      try {
        whiteListFileContent = IOUtils.toByteArray(Files.newInputStream(whiteListFile));
      }
      catch(IOException ioe){
        throw new RuntimeException("Unable to read file " + whiteListFile , ioe);
      }
    } else {
      whiteListFileContent = null;
    }

    this.securityRootDirectory = securityRootDirectory.toString();
  }

  public static SecurityRootDirectory securityRootDirectory(URL securityRootDirectoryUrl) {
    try {
      return new SecurityRootDirectory(Paths.get(securityRootDirectoryUrl.toURI()));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static SecurityRootDirectory securityRootDirectory(Path securityRootDirectoryPath) {
    return new SecurityRootDirectory(securityRootDirectoryPath);
  }

  private static void storeContentsToDir(Map<String, byte[]> map, Path directory) {

    for (Map.Entry<String, byte[]> entry : map.entrySet()) {
      Path filePath = directory.resolve(entry.getKey());

      byte[] fileContents = entry.getValue();

      try {
        Files.write(filePath, fileContents);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create file " + filePath, e);
      }
    }
  }

  private static void storeContentsToMap(Path directory, Map<String, byte[]> map) {

    try {
      Files.list(directory).forEach((path) -> {
        try {
          map.put(path.getFileName().toString(), IOUtils.toByteArray(Files.newInputStream(path)));
        } catch (IOException e) {
          throw new RuntimeException("Unable to read file " + path, e);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Unable to read directory " + directory, e);
    }
  }

  public Path getSecurityRootDirectory() {
    return Paths.get(securityRootDirectory);
  }

  public void createSecurityRootDirectory(Path newSecurityRootDirectory) {
    Path identityDir = newSecurityRootDirectory.resolve(IDENTITY_DIR_NAME);
    if (identityMap != null) {
      try {
        Files.createDirectories(identityDir);
        storeContentsToDir(identityMap, identityDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + identityDir, e);
      }
    }

    Path trustedAuthorityDir = newSecurityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    if (trustedAuthorityMap != null) {
      try {
        Files.createDirectories(trustedAuthorityDir);
        storeContentsToDir(trustedAuthorityMap, trustedAuthorityDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + trustedAuthorityDir, e);
      }
    }

    Path accessControlDir = newSecurityRootDirectory.resolve(ACCESS_CONTROL_DIR_NAME);
    if (accessControlMap != null) {
      try {
        Files.createDirectories(accessControlDir);
        storeContentsToDir(accessControlMap, accessControlDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + accessControlDir, e);
      }
    }

    Path whiteListDeprecatedDir = newSecurityRootDirectory.resolve(WHITE_LIST_DEPRECATED_DIR_NAME);
    if (whiteListDeprecatedMap != null) {
      try {
        Files.createDirectories(whiteListDeprecatedDir);
        storeContentsToDir(whiteListDeprecatedMap, whiteListDeprecatedDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + whiteListDeprecatedDir, e);
      }
    }

    if(whiteListFileContent != null){
      try {
        Files.createDirectories(newSecurityRootDirectory);
        Path whiteListFile = newSecurityRootDirectory.resolve(WHITE_LIST_FILE_NAME);
        Files.write(whiteListFile, whiteListFileContent);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create whitelist file ", e);
      }
    }
  }
}
