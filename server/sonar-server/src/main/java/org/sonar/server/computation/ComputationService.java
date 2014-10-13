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

package org.sonar.server.computation;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerComponent;
import org.sonar.core.computation.db.AnalysisReportDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.server.computation.db.AnalysisReportDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.issue.index.IssueAuthorizationIndex;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.permission.InternalPermissionService;
import org.sonar.server.search.IndexClient;

import java.util.List;

/**
 * since 5.0
 */
public class ComputationService implements ServerComponent {
  private static final Logger LOG = LoggerFactory.getLogger(ComputationService.class);

  private final DbClient dbClient;
  private final AnalysisReportDao dao;
  private final IndexClient index;
  private final InternalPermissionService permissionService;

  public ComputationService(DbClient dbClient, IndexClient index, InternalPermissionService permissionService) {
    this.dbClient = dbClient;
    this.dao = this.dbClient.analysisReportDao();
    this.index = index;
    this.permissionService = permissionService;
  }

  public void analyzeReport(AnalysisReportDto report) {
    // Synchronization of lot of data can only be done with a batch session for the moment
    DbSession session = dbClient.openSession(true);
    String projectKey = report.getProjectKey();

    try {
      synchronizeProjectPermissionsIfNotFound(session, projectKey);
      indexProjectIssues(session, projectKey);
    } finally {
      MyBatis.closeQuietly(session);
    }

    LOG.info(String.format("Analysis of %s successfully finished.", report));
  }

  private void indexProjectIssues(DbSession session, String projectKey) {
    dbClient.issueDao().synchronizeAfter(session,
      index.get(IssueIndex.class).getLastSynchronization(),
      ImmutableMap.of("project", projectKey));
    session.commit();
  }

  private void synchronizeProjectPermissionsIfNotFound(DbSession session, String projectKey) {
    if (index.get(IssueAuthorizationIndex.class).getNullableByKey(projectKey) == null) {
      permissionService.synchronizePermissions(session, projectKey);
      session.commit();
    }
  }

  public List<AnalysisReportDto> findAllUnfinishedAnalysisReports() {
    return null;
  }
}
