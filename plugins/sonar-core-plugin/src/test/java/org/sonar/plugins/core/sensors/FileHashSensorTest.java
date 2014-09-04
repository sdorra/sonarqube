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
package org.sonar.plugins.core.sensors;

import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DeprecatedDefaultInputFile;
import org.sonar.api.resources.Project;
import org.sonar.batch.index.ComponentDataCache;
import org.sonar.batch.scan.filesystem.InputPathCache;
import org.sonar.core.source.SnapshotDataTypes;

import java.util.Collections;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FileHashSensorTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  Project project = new Project("struts");
  InputPathCache fileCache = mock(InputPathCache.class);
  ComponentDataCache componentDataCache = mock(ComponentDataCache.class);
  FileHashSensor sensor = new FileHashSensor(fileCache, componentDataCache);

  @Test
  public void store_file_hashes() throws Exception {
    when(fileCache.filesByModule("struts")).thenReturn(Lists.<InputFile>newArrayList(
      new DeprecatedDefaultInputFile("foo", "src/Foo.java").setFile(temp.newFile()).setHash("ABC"),
      new DeprecatedDefaultInputFile("foo", "src/Bar.java").setFile(temp.newFile()).setHash("DEF")));

    SensorContext sensorContext = mock(SensorContext.class);
    sensor.analyse(project, sensorContext);

    verify(componentDataCache).setStringData("struts", SnapshotDataTypes.FILE_HASHES, "src/Foo.java=ABC;src/Bar.java=DEF");
    verifyZeroInteractions(sensorContext);
  }

  @Test
  public void store_file_hashes_for_branches() throws Exception {
    project = new Project("struts", "branch-2.x", "Struts 2.x");
    when(fileCache.filesByModule("struts:branch-2.x")).thenReturn(Lists.<InputFile>newArrayList(
      new DeprecatedDefaultInputFile("foo", "src/Foo.java").setFile(temp.newFile()).setHash("ABC"),
      new DeprecatedDefaultInputFile("foo", "src/Bar.java").setFile(temp.newFile()).setHash("DEF")));

    SensorContext sensorContext = mock(SensorContext.class);
    sensor.analyse(project, sensorContext);

    verify(componentDataCache).setStringData("struts:branch-2.x", SnapshotDataTypes.FILE_HASHES, "src/Foo.java=ABC;src/Bar.java=DEF");
    verifyZeroInteractions(sensorContext);
  }

  @Test
  public void various_tests() throws Exception {
    assertThat(sensor.shouldExecuteOnProject(project)).isTrue();
    assertThat(sensor.toString()).isEqualTo("FileHashSensor");
  }

  @Test
  public void dont_save_hashes_if_no_files() throws Exception {
    when(fileCache.filesByModule("struts")).thenReturn(Collections.<InputFile>emptyList());

    SensorContext sensorContext = mock(SensorContext.class);
    sensor.analyse(project, sensorContext);

    verifyZeroInteractions(componentDataCache);
    verifyZeroInteractions(sensorContext);
  }
}
