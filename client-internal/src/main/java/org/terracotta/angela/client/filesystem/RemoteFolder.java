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
package org.terracotta.angela.client.filesystem;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;
import org.terracotta.angela.agent.AgentController;
import org.terracotta.angela.agent.com.AgentExecutor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

public class RemoteFolder extends RemoteFile {

  public RemoteFolder(AgentExecutor agentExecutor, String parentName, String name) {
    super(agentExecutor, parentName, name);
  }

  public List<RemoteFile> list() {
    String absoluteName = getAbsoluteName();
    List<String> remoteFiles = agentExecutor.execute(() -> AgentController.getInstance().listFiles(absoluteName));
    List<String> remoteFolders = agentExecutor.execute(() -> AgentController.getInstance().listFolders(absoluteName));

    List<RemoteFile> result = new ArrayList<>();
    result.addAll(remoteFiles.stream()
        .map(s -> new RemoteFile(agentExecutor, getAbsoluteName(), s))
        .collect(toList()));
    result.addAll(remoteFolders.stream()
        .map(s -> new RemoteFolder(agentExecutor, getAbsoluteName(), s))
        .collect(toList()));
    return result;
  }

  public void upload(File localFile) throws IOException {
    upload(localFile.toPath());
  }

  @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
  public void upload(Path localFile) throws IOException {
    if (Files.isDirectory(localFile)) {
      uploadFolder(".", localFile);
    } else {
      try (InputStream fis = Files.newInputStream(localFile)) {
        upload(localFile.getFileName().toString(), fis);
      }
    }
  }

  @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
  private void uploadFolder(String parentName, Path folder) throws IOException {
    try (Stream<Path> stream = Files.list(folder)) {
      stream.forEach(f -> {
        try {
          String currentName = parentName + "/" + f.getFileName();
          if (Files.isDirectory(f)) {
            uploadFolder(currentName, f);
          } else {
            try (InputStream fis = Files.newInputStream(f)) {
              upload(currentName, fis);
            }
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
  }

  public void upload(String remoteFilename, URL localResourceUrl) throws IOException {
    try (InputStream in = localResourceUrl.openStream()) {
      upload(remoteFilename, in);
    }
  }

  public void upload(String remoteFilename, InputStream localStream) throws IOException {
    byte[] data = IOUtils.toByteArray(localStream);
    String filename = getAbsoluteName() + "/" + remoteFilename;
    agentExecutor.execute(() -> AgentController.getInstance().uploadFile(filename, data));
  }

  @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
  @Override
  public void downloadTo(Path localPath) throws IOException {
    String foldername = getAbsoluteName();
    byte[] bytes;
    try {
      bytes = agentExecutor.execute(() -> AgentController.getInstance().downloadFolder(foldername));
    } catch (RuntimeException e) {
      throw new IOException("Error downloading remote folder '" + foldername + "' into local folder '" + localPath + "'", e);
    }

    Files.createDirectories(localPath);

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      while (true) {
        ZipEntry nextEntry = zis.getNextEntry();
        if (nextEntry == null) {
          break;
        }
        String name = nextEntry.getName();
        Path file = localPath.resolve(name);
        Files.createDirectories(file.getParent());
        try (OutputStream fos = Files.newOutputStream(file)) {
          IOUtils.copy(zis, fos);
        }
      }
    }
  }

  @Override
  public String toString() {
    return super.toString() + "/";
  }
}
