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
package org.sonar.server.issue.db;

import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.issue.db.IssueDto;
import org.sonar.core.persistence.AbstractDaoTestCase;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.rule.RuleTesting;

import java.util.Date;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueDaoTest extends AbstractDaoTestCase {

  private IssueDao dao;
  private DbSession session;
  private System2 system2;

  @Before
  public void before() throws Exception {
    this.session = getMyBatis().openSession(false);
    this.system2 = mock(System2.class);
    this.dao = new IssueDao(system2);
  }

  @After
  public void after() {
    this.session.close();
  }

  @Test
  public void get_by_key() {
    setupData("shared", "get_by_key");

    IssueDto issue = dao.getByKey(session, "ABCDE");
    assertThat(issue.getKee()).isEqualTo("ABCDE");
    assertThat(issue.getId()).isEqualTo(100L);
    assertThat(issue.getComponentId()).isEqualTo(401);
    assertThat(issue.getRootComponentId()).isEqualTo(399);
    assertThat(issue.getRuleId()).isEqualTo(500);
    assertThat(issue.getLanguage()).isEqualTo("java");
    assertThat(issue.getSeverity()).isEqualTo("BLOCKER");
    assertThat(issue.isManualSeverity()).isFalse();
    assertThat(issue.getMessage()).isNull();
    assertThat(issue.getLine()).isEqualTo(200);
    assertThat(issue.getEffortToFix()).isEqualTo(4.2);
    assertThat(issue.getStatus()).isEqualTo("OPEN");
    assertThat(issue.getResolution()).isEqualTo("FIXED");
    assertThat(issue.getChecksum()).isEqualTo("XXX");
    assertThat(issue.getAuthorLogin()).isEqualTo("karadoc");
    assertThat(issue.getReporter()).isEqualTo("arthur");
    assertThat(issue.getAssignee()).isEqualTo("perceval");
    assertThat(issue.getIssueAttributes()).isEqualTo("JIRA=FOO-1234");
    assertThat(issue.getIssueCreationDate()).isNotNull();
    assertThat(issue.getIssueUpdateDate()).isNotNull();
    assertThat(issue.getIssueCloseDate()).isNotNull();
    assertThat(issue.getCreatedAt()).isNotNull();
    assertThat(issue.getUpdatedAt()).isNotNull();
    assertThat(issue.getRuleRepo()).isEqualTo("squid");
    assertThat(issue.getRule()).isEqualTo("AvoidCycle");
    assertThat(issue.getComponentKey()).isEqualTo("Action.java");
    assertThat(issue.getRootComponentKey()).isEqualTo("struts");
  }

  @Test
  public void get_by_keys() {
    setupData("shared", "get_by_key");

    List<IssueDto> issues = dao.getByKeys(session, "ABCDE");
    assertThat(issues).hasSize(1);

    IssueDto issue = issues.get(0);
    assertThat(issue.getKee()).isEqualTo("ABCDE");
    assertThat(issue.getId()).isEqualTo(100L);
    assertThat(issue.getComponentId()).isEqualTo(401);
    assertThat(issue.getRootComponentId()).isEqualTo(399);
    assertThat(issue.getRuleId()).isEqualTo(500);
    assertThat(issue.getLanguage()).isEqualTo("java");
    assertThat(issue.getSeverity()).isEqualTo("BLOCKER");
    assertThat(issue.isManualSeverity()).isFalse();
    assertThat(issue.getMessage()).isNull();
    assertThat(issue.getLine()).isEqualTo(200);
    assertThat(issue.getEffortToFix()).isEqualTo(4.2);
    assertThat(issue.getStatus()).isEqualTo("OPEN");
    assertThat(issue.getResolution()).isEqualTo("FIXED");
    assertThat(issue.getChecksum()).isEqualTo("XXX");
    assertThat(issue.getAuthorLogin()).isEqualTo("karadoc");
    assertThat(issue.getReporter()).isEqualTo("arthur");
    assertThat(issue.getAssignee()).isEqualTo("perceval");
    assertThat(issue.getIssueAttributes()).isEqualTo("JIRA=FOO-1234");
    assertThat(issue.getIssueCreationDate()).isNotNull();
    assertThat(issue.getIssueUpdateDate()).isNotNull();
    assertThat(issue.getIssueCloseDate()).isNotNull();
    assertThat(issue.getCreatedAt()).isNotNull();
    assertThat(issue.getUpdatedAt()).isNotNull();
    assertThat(issue.getRuleRepo()).isEqualTo("squid");
    assertThat(issue.getRule()).isEqualTo("AvoidCycle");
    assertThat(issue.getComponentKey()).isEqualTo("Action.java");
    assertThat(issue.getRootComponentKey()).isEqualTo("struts");
  }

  @Test
  public void find_by_action_plan() {
    setupData("shared", "find_by_action_plan");

    List<IssueDto> issues = dao.findByActionPlan(session, "AP-1");
    assertThat(issues).hasSize(1);

    IssueDto issue = issues.get(0);
    assertThat(issue.getKee()).isEqualTo("ABCDE");
    assertThat(issue.getActionPlanKey()).isEqualTo("AP-1");
    assertThat(issue.getComponentId()).isEqualTo(401);
    assertThat(issue.getRootComponentId()).isEqualTo(399);
    assertThat(issue.getRuleId()).isEqualTo(500);
    assertThat(issue.getLanguage()).isEqualTo("java");
    assertThat(issue.getSeverity()).isEqualTo("BLOCKER");
    assertThat(issue.isManualSeverity()).isFalse();
    assertThat(issue.getMessage()).isNull();
    assertThat(issue.getLine()).isEqualTo(200);
    assertThat(issue.getEffortToFix()).isEqualTo(4.2);
    assertThat(issue.getStatus()).isEqualTo("OPEN");
    assertThat(issue.getResolution()).isEqualTo("FIXED");
    assertThat(issue.getChecksum()).isEqualTo("XXX");
    assertThat(issue.getAuthorLogin()).isEqualTo("karadoc");
    assertThat(issue.getReporter()).isEqualTo("arthur");
    assertThat(issue.getAssignee()).isEqualTo("perceval");
    assertThat(issue.getIssueAttributes()).isEqualTo("JIRA=FOO-1234");
    assertThat(issue.getIssueCreationDate()).isNotNull();
    assertThat(issue.getIssueUpdateDate()).isNotNull();
    assertThat(issue.getIssueCloseDate()).isNotNull();
    assertThat(issue.getCreatedAt()).isNotNull();
    assertThat(issue.getUpdatedAt()).isNotNull();
    assertThat(issue.getRuleRepo()).isEqualTo("squid");
    assertThat(issue.getRule()).isEqualTo("AvoidCycle");
    assertThat(issue.getComponentKey()).isEqualTo("Action.java");
    assertThat(issue.getRootComponentKey()).isEqualTo("struts");
  }

  @Test
  public void find_after_dates() throws Exception {
    setupData("shared", "some_issues");

    Date t0 = new Date(0);
    assertThat(dao.findAfterDate(session, t0)).hasSize(3);

    Date t2014 = DateUtils.parseDate("2014-01-01");
    assertThat(dao.findAfterDate(session, t2014)).hasSize(1);
  }

  @Test
  public void find_after_dates_with_project() throws Exception {
    setupData("shared", "find_after_dates_with_project");

    assertThat(dao.findAfterDate(session, DateUtils.parseDate("2014-01-01"), ImmutableMap.of("project", "struts"))).hasSize(1);
  }

  @Test
  public void insert() throws Exception {
    when(system2.now()).thenReturn(DateUtils.parseDate("2013-05-22").getTime());

    IssueDto dto = new IssueDto();
    dto.setComponent(new ComponentDto().setKey("struts:Action").setId(123L));
    dto.setRootComponent(new ComponentDto().setKey("struts").setId(100L));
    dto.setRule(RuleTesting.newDto(RuleKey.of("squid", "S001")).setId(200));
    dto.setKee("ABCDE");
    dto.setLine(500);
    dto.setEffortToFix(3.14);
    dto.setDebt(10L);
    dto.setResolution("FIXED");
    dto.setStatus("RESOLVED");
    dto.setSeverity("BLOCKER");
    dto.setReporter("emmerik");
    dto.setAuthorLogin("morgan");
    dto.setAssignee("karadoc");
    dto.setActionPlanKey("current_sprint");
    dto.setIssueAttributes("JIRA=FOO-1234");
    dto.setChecksum("123456789");
    dto.setMessage("the message");

    dto.setIssueCreationDate(DateUtils.parseDate("2013-05-18"));
    dto.setIssueUpdateDate(DateUtils.parseDate("2013-05-19"));
    dto.setIssueCloseDate(DateUtils.parseDate("2013-05-20"));
    dto.setCreatedAt(DateUtils.parseDate("2013-05-21"));
    dto.setUpdatedAt(DateUtils.parseDate("2013-05-22"));

    dao.insert(session, dto);
    session.commit();

    checkTables("insert", new String[]{"id"}, "issues");
  }

  @Test
  public void update() throws Exception {
    when(system2.now()).thenReturn(DateUtils.parseDate("2013-05-22").getTime());

    setupData("update");

    IssueDto dto = new IssueDto();
    dto.setComponent(new ComponentDto().setKey("struts:Action").setId(123L));
    dto.setRootComponent(new ComponentDto().setKey("struts").setId(101L));
    dto.setRule(RuleTesting.newDto(RuleKey.of("squid", "S001")).setId(200));
    dto.setKee("ABCDE");
    dto.setLine(500);
    dto.setEffortToFix(3.14);
    dto.setDebt(10L);
    dto.setResolution("FIXED");
    dto.setStatus("RESOLVED");
    dto.setSeverity("BLOCKER");
    dto.setReporter("emmerik");
    dto.setAuthorLogin("morgan");
    dto.setAssignee("karadoc");
    dto.setActionPlanKey("current_sprint");
    dto.setIssueAttributes("JIRA=FOO-1234");
    dto.setChecksum("123456789");
    dto.setMessage("the message");

    dto.setIssueCreationDate(DateUtils.parseDate("2013-05-18"));
    dto.setIssueUpdateDate(DateUtils.parseDate("2013-05-19"));
    dto.setIssueCloseDate(DateUtils.parseDate("2013-05-20"));
    dto.setCreatedAt(DateUtils.parseDate("2013-05-21"));
    dto.setUpdatedAt(DateUtils.parseDate("2013-05-22"));

    dao.update(session, dto);
    session.commit();

    checkTables("update", new String[]{"id"}, "issues");
  }
}
