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

package org.sonar.core.issue.db;

import org.junit.Before;
import org.junit.Test;
import org.sonar.core.persistence.AbstractDaoTestCase;
import org.sonar.server.issue.IssueQuery;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class IssueStatsDaoTest extends AbstractDaoTestCase {

  IssueStatsDao dao;

  @Before
  public void createDao() {
    dao = new IssueStatsDao(getMyBatis());
  }

  @Test
  public void should_select_assignees(){
    setupData("should_select_assignees");

    IssueQuery query = IssueQuery.builder().requiredRole("user").build();
    List<Object> results = dao.selectIssuesColumn(query, IssueStatsColumn.ASSIGNEE, null);
    assertThat(results).hasSize(3);
    // 2 perceval, and one null
    assertThat(results).containsOnly("perceval", null);
  }
}
