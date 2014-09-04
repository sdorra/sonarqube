/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.scan.filesystem;

import org.apache.commons.io.FilenameUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DeprecatedDefaultInputFile;
import org.sonar.api.scan.filesystem.FileSystemFilter;
import org.sonar.api.scan.filesystem.FileType;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeprecatedFileFiltersTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  FileSystemFilter filter = mock(FileSystemFilter.class);

  @Test
  public void no_filters() throws Exception {
    DeprecatedFileFilters filters = new DeprecatedFileFilters();

    InputFile inputFile = new DeprecatedDefaultInputFile("foo", "src/main/java/Foo.java").setFile(temp.newFile());
    assertThat(filters.accept(inputFile)).isTrue();
  }

  @Test
  public void at_least_one_filter() throws Exception {
    DeprecatedFileFilters filters = new DeprecatedFileFilters(new FileSystemFilter[] {filter});

    File basedir = temp.newFolder();
    File file = temp.newFile();
    InputFile inputFile = new DeprecatedDefaultInputFile("foo", "src/main/java/Foo.java")
      .setSourceDirAbsolutePath(new File(basedir, "src/main/java").getAbsolutePath())
      .setPathRelativeToSourceDir("Foo.java")
      .setFile(file)
      .setType(InputFile.Type.MAIN);
    when(filter.accept(eq(file), any(DeprecatedFileFilters.DeprecatedContext.class))).thenReturn(false);

    assertThat(filters.accept(inputFile)).isFalse();

    ArgumentCaptor<DeprecatedFileFilters.DeprecatedContext> argument = ArgumentCaptor.forClass(DeprecatedFileFilters.DeprecatedContext.class);
    verify(filter).accept(eq(file), argument.capture());

    DeprecatedFileFilters.DeprecatedContext context = argument.getValue();
    assertThat(context.canonicalPath()).isEqualTo(FilenameUtils.separatorsToUnix(file.getAbsolutePath()));
    assertThat(context.relativeDir()).isEqualTo(new File(basedir, "src/main/java"));
    assertThat(context.relativePath()).isEqualTo("Foo.java");
    assertThat(context.type()).isEqualTo(FileType.MAIN);
  }
}
