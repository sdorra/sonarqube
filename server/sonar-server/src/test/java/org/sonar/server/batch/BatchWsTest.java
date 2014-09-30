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
package org.sonar.server.batch;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.resources.Languages;
import org.sonar.core.properties.PropertiesDao;
import org.sonar.server.computation.ComputationService;
import org.sonar.server.db.DbClient;
import org.sonar.server.permission.InternalPermissionService;
import org.sonar.server.qualityprofile.QProfileFactory;
import org.sonar.server.qualityprofile.QProfileLoader;
import org.sonar.server.rule.RuleService;
import org.sonar.server.search.IndexClient;
import org.sonar.server.ws.WsTester;

import java.io.File;
import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BatchWsTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  BatchIndex batchIndex;

  WsTester tester;

  @Before
  public void before() throws IOException {
    tester = new WsTester(new BatchWs(batchIndex,
      new GlobalReferentialsAction(mock(DbClient.class), mock(PropertiesDao.class)),
      new ProjectReferentialsAction(mock(DbClient.class), mock(PropertiesDao.class), mock(QProfileFactory.class), mock(QProfileLoader.class), mock(RuleService.class),
        mock(Languages.class)),
      new UploadReportAction(mock(DbClient.class), mock(IndexClient.class), mock(InternalPermissionService.class), mock(ComputationService.class))));
  }

  @Test
  public void download_index() throws Exception {
    when(batchIndex.getIndex()).thenReturn("sonar-batch.jar|acbd18db4cc2f85cedef654fccc4a4d8");

    String index = tester.newGetRequest("batch", "index").execute().outputAsString();
    assertThat(index).isEqualTo("sonar-batch.jar|acbd18db4cc2f85cedef654fccc4a4d8");
  }

  @Test
  public void download_file() throws Exception {
    String filename = "sonar-batch.jar";

    File file = temp.newFile(filename);
    FileUtils.writeStringToFile(file, "foo");
    when(batchIndex.getFile(filename)).thenReturn(file);

    String jar = tester.newGetRequest("batch", "file").setParam("name", filename).execute().outputAsString();
    assertThat(jar).isEqualTo("foo");
  }

}
