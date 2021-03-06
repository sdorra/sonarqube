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
package org.sonar.server.issue;

import org.sonar.api.ServerComponent;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.rules.RuleFinder;
import org.sonar.core.issue.db.IssueDto;
import org.sonar.core.issue.db.IssueStorage;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.server.db.DbClient;

import java.util.Date;

/**
 * @since 3.6
 */
public class ServerIssueStorage extends IssueStorage implements ServerComponent {

  private final DbClient dbClient;

  public ServerIssueStorage(MyBatis mybatis, RuleFinder ruleFinder, DbClient dbClient) {
    super(mybatis, ruleFinder);
    this.dbClient = dbClient;
  }

  @Override
  protected void doInsert(DbSession session, Date now, DefaultIssue issue) {
    long componentId = componentId(session, issue);
    long projectId = projectId(session, issue);
    int ruleId = ruleId(issue);
    IssueDto dto = IssueDto.toDtoForInsert(issue, componentId, projectId, ruleId, now);

    dbClient.issueDao().insert(session, dto);
  }

  @Override
  protected void doUpdate(DbSession session, Date now, DefaultIssue issue) {
    IssueDto dto = IssueDto.toDtoForUpdate(issue, projectId(session, issue), now);

    dbClient.issueDao().update(session, dto);
  }

  protected long componentId(DbSession session, DefaultIssue issue) {
    return dbClient.componentDao().getAuthorizedComponentByKey(issue.componentKey(), session).getId();
  }

  protected long projectId(DbSession session, DefaultIssue issue) {
    return dbClient.componentDao().getAuthorizedComponentByKey(issue.projectKey(), session).getId();
  }
}
