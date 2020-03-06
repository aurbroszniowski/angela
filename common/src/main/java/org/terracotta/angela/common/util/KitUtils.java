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
package org.terracotta.angela.common.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.terracotta.angela.common.util.FileUtils.setCorrectPermissions;

public class KitUtils {
  public static void extractZip(Path kitInstaller, Path kitDest) {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(kitInstaller)))) {
      extractArchive(archiveIs, kitDest);
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    setCorrectPermissions(kitDest);
  }

  public static void extractArchive(ArchiveInputStream archiveIs, Path pathOutput) throws IOException {
    while (true) {
      ArchiveEntry archiveEntry = archiveIs.getNextEntry();
      if (archiveEntry == null) {
        break;
      }

      Path pathEntryOutput = pathOutput.resolve(archiveEntry.getName());
      if (!archiveEntry.isDirectory()) {
        Path parentPath = pathEntryOutput.getParent();
        if (!Files.isDirectory(parentPath)) {
          Files.createDirectories(parentPath);
        }
        Files.copy(archiveIs, pathEntryOutput);
      }
    }
  }

  public static void extractTarGz(Path kitInstaller, Path kitDest) {
    try (ArchiveInputStream archiveIs = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files
        .newInputStream(kitInstaller))))) {
      extractArchive(archiveIs, kitDest);
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    setCorrectPermissions(kitDest);
  }

  public static String getParentDirFromTarGz(Path localInstaller) {
    try (TarArchiveInputStream archiveIs = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new UncheckedIOException("Error when getting parent dir from archive", ioe);
    }
  }
}
