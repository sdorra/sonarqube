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

package org.sonar.server.computation.ws;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.utils.DateUtils;
import org.sonar.core.computation.db.AnalysisReportDto;
import org.sonar.server.computation.ComputationService;
import org.sonar.server.ws.WsTester;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.core.computation.db.AnalysisReportDto.Status.PENDING;

public class ActiveReportsActionTest {

  ComputationService service = mock(ComputationService.class);

  WsTester tester;

  @Before
  public void setup() throws Exception {
    tester = new WsTester(new AnalysisReportWebService(new ActiveReportsAction(service)));
  }

  @Test
  public void list_active_reports() throws Exception {
    AnalysisReportDto report = AnalysisReportDto
      .newForTests(1L)
      .setProjectName("Project Name")
      .setProjectKey("project-name")
      .setStatus(PENDING)
      .setData(null);
    report.setCreatedAt(DateUtils.parseDateTime("2014-10-13T00:00:00+0200"))
      .setUpdatedAt(DateUtils.parseDateTime("2014-10-14T00:00:00+0200"));
    List<AnalysisReportDto> reports = Lists.newArrayList(report);
    when(service.findAllUnfinishedAnalysisReports()).thenReturn(reports);

    WsTester.TestRequest request = tester.newGetRequest(AnalysisReportWebService.API_ENDPOINT, "active");
    request.execute().assertJson(getClass(), "list_active_reports.json");
  }

  @Test
  public void define() throws Exception {
    assertThat(tester.controller(AnalysisReportWebService.API_ENDPOINT).action("active")).isNotNull();
  }

}
