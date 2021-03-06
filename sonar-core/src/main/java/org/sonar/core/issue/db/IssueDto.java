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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.Duration;
import org.sonar.api.utils.KeyValueFormat;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.persistence.Dto;
import org.sonar.core.rule.RuleDto;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * @since 3.6
 */
public final class IssueDto extends Dto<String> implements Serializable {

  private Long id;
  private String kee;
  private Long componentId;
  private Long rootComponentId;
  private Integer ruleId;
  private String severity;
  private boolean manualSeverity;
  private String message;
  private Integer line;
  private Double effortToFix;
  private Long debt;
  private String status;
  private String resolution;
  private String checksum;
  private String reporter;
  private String assignee;
  private String authorLogin;
  private String actionPlanKey;
  private String issueAttributes;

  // functional dates
  private Date issueCreationDate;
  private Date issueUpdateDate;
  private Date issueCloseDate;

  /**
   * Temporary date used only during scan
   */
  private Date selectedAt;

  // joins
  private String ruleKey;
  private String ruleRepo;
  private String language;
  private String componentKey;
  private String rootComponentKey;

  @Override
  public String getKey() {
    return getKee();
  }

  public Long getId() {
    return id;
  }

  public IssueDto setId(@Nullable Long id) {
    this.id = id;
    return this;
  }

  public String getKee() {
    return kee;
  }

  public IssueDto setKee(String s) {
    this.kee = s;
    return this;
  }

  public Long getComponentId() {
    return componentId;
  }

  public IssueDto setComponent(ComponentDto component) {
    this.componentId = component.getId();
    this.componentKey = component.getKey();
    return this;
  }

  /**
   * please use setComponent(ComponentDto component)
   */
  public IssueDto setComponentId(Long componentId) {
    this.componentId = componentId;
    return this;
  }

  public Long getRootComponentId() {
    return rootComponentId;
  }

  public IssueDto setRootComponent(ComponentDto rootComponent) {
    this.rootComponentId = rootComponent.getId();
    this.rootComponentKey = rootComponent.getKey();
    return this;
  }

  /**
   * please use setRootComponent
   */
  public IssueDto setRootComponentId(Long rootComponentId) {
    this.rootComponentId = rootComponentId;
    return this;
  }

  public Integer getRuleId() {
    return ruleId;
  }

  public IssueDto setRule(RuleDto rule) {
    Preconditions.checkNotNull(rule.getId(), "Rule must be persisted.");
    this.ruleId = rule.getId();
    this.ruleKey = rule.getRuleKey();
    this.ruleRepo = rule.getRepositoryKey();
    this.language = rule.getLanguage();
    return this;
  }

  /**
   * please use setRule(RuleDto rule)
   */
  public IssueDto setRuleId(Integer ruleId) {
    this.ruleId = ruleId;
    return this;
  }

  @CheckForNull
  public String getActionPlanKey() {
    return actionPlanKey;
  }

  public IssueDto setActionPlanKey(@Nullable String s) {
    this.actionPlanKey = s;
    return this;
  }

  @CheckForNull
  public String getSeverity() {
    return severity;
  }

  public IssueDto setSeverity(@Nullable String severity) {
    this.severity = severity;
    return this;
  }

  public boolean isManualSeverity() {
    return manualSeverity;
  }

  public IssueDto setManualSeverity(boolean manualSeverity) {
    this.manualSeverity = manualSeverity;
    return this;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  public IssueDto setMessage(@Nullable String s) {
    this.message = s;
    return this;
  }

  @CheckForNull
  public Integer getLine() {
    return line;
  }

  public IssueDto setLine(@Nullable Integer line) {
    this.line = line;
    return this;
  }

  @CheckForNull
  public Double getEffortToFix() {
    return effortToFix;
  }

  public IssueDto setEffortToFix(@Nullable Double d) {
    this.effortToFix = d;
    return this;
  }

  @CheckForNull
  public Long getDebt() {
    return debt;
  }

  public IssueDto setDebt(@Nullable Long debt) {
    this.debt = debt;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public IssueDto setStatus(@Nullable String status) {
    this.status = status;
    return this;
  }

  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public IssueDto setResolution(@Nullable String s) {
    this.resolution = s;
    return this;
  }

  @CheckForNull
  public String getChecksum() {
    return checksum;
  }

  public IssueDto setChecksum(@Nullable String checksum) {
    this.checksum = checksum;
    return this;
  }

  @CheckForNull
  public String getReporter() {
    return reporter;
  }

  public IssueDto setReporter(@Nullable String s) {
    this.reporter = s;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public IssueDto setAssignee(@Nullable String s) {
    this.assignee = s;
    return this;
  }

  public String getAuthorLogin() {
    return authorLogin;
  }

  public IssueDto setAuthorLogin(@Nullable String authorLogin) {
    this.authorLogin = authorLogin;
    return this;
  }

  public String getIssueAttributes() {
    return issueAttributes;
  }

  public IssueDto setIssueAttributes(@Nullable String s) {
    Preconditions.checkArgument(s == null || s.length() <= 4000,
      "Issue attributes must not exceed 4000 characters: " + s);
    this.issueAttributes = s;
    return this;
  }

  @Override
  public IssueDto setCreatedAt(Date createdAt) {
    super.setCreatedAt(createdAt);
    return this;
  }

  @Override
  public IssueDto setUpdatedAt(@Nullable Date updatedAt) {
    super.setUpdatedAt(updatedAt);
    return this;
  }

  public Date getIssueCreationDate() {
    return issueCreationDate;
  }

  public IssueDto setIssueCreationDate(@Nullable Date d) {
    this.issueCreationDate = d;
    return this;
  }

  public Date getIssueUpdateDate() {
    return issueUpdateDate;
  }

  public IssueDto setIssueUpdateDate(@Nullable Date d) {
    this.issueUpdateDate = d;
    return this;
  }

  public Date getIssueCloseDate() {
    return issueCloseDate;
  }

  public IssueDto setIssueCloseDate(@Nullable Date d) {
    this.issueCloseDate = d;
    return this;
  }

  public String getRule() {
    return ruleKey;
  }

  public String getRuleRepo() {
    return ruleRepo;
  }

  public RuleKey getRuleKey(){
    return RuleKey.of(ruleRepo, ruleKey);
  }

  public String getLanguage(){
    return language;
  }

  public String getComponentKey() {
    return componentKey;
  }

  public String getRootComponentKey() {
    return rootComponentKey;
  }

  @CheckForNull
  public Date getSelectedAt() {
    return selectedAt;
  }

  public IssueDto setSelectedAt(@Nullable Date d) {
    this.selectedAt = d;
    return this;
  }

  /**
   * Should only be used to persist in E/S
   *
   * Please use {@link #setRule(org.sonar.core.rule.RuleDto)} instead
   */
  public IssueDto setRuleKey(String repo, String rule) {
    this.ruleRepo = repo;
    this.ruleKey = rule;
    return this;
  }

  /**
   * Should only be used to persist in E/S
   *
   * Please use {@link #setRule(org.sonar.core.rule.RuleDto)} instead
   */
  public IssueDto setLanguage(String language) {
    this.language = language;
    return this;
  }

  /**
   * Should only be used to persist in E/S
   *
   * Please use {@link #setComponent(org.sonar.core.component.ComponentDto)} instead
   */
  public IssueDto setComponentKey(String componentKey) {
    this.componentKey = componentKey;
    return this;
  }

  /**
   * Should only be used to persist in E/S
   *
   * Please use {@link #setRootComponent(org.sonar.core.component.ComponentDto)} instead
   */
  public IssueDto setRootComponentKey(String rootComponentKey) {
    this.rootComponentKey = rootComponentKey;
    return this;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  public static IssueDto toDtoForInsert(DefaultIssue issue, Long componentId, Long rootComponentId, Integer ruleId, Date now) {
    return new IssueDto()
      .setKee(issue.key())
      .setLine(issue.line())
      .setMessage(issue.message())
      .setEffortToFix(issue.effortToFix())
      .setDebt(issue.debtInMinutes())
      .setResolution(issue.resolution())
      .setStatus(issue.status())
      .setSeverity(issue.severity())
      .setChecksum(issue.checksum())
      .setManualSeverity(issue.manualSeverity())
      .setReporter(issue.reporter())
      .setAssignee(issue.assignee())
      .setRuleId(ruleId)
      .setRuleKey(issue.ruleKey().repository(), issue.ruleKey().rule())
      .setComponentId(componentId)
      .setComponentKey(issue.componentKey())
      .setRootComponentId(rootComponentId)
      .setRootComponentKey(issue.projectKey())
      .setActionPlanKey(issue.actionPlanKey())
      .setIssueAttributes(KeyValueFormat.format(issue.attributes()))
      .setAuthorLogin(issue.authorLogin())
      .setIssueCreationDate(issue.creationDate())
      .setIssueCloseDate(issue.closeDate())
      .setIssueUpdateDate(issue.updateDate())
      .setSelectedAt(issue.selectedAt())
      .setCreatedAt(now)
      .setUpdatedAt(now);
  }

  public static IssueDto toDtoForUpdate(DefaultIssue issue, Long rootComponentId, Date now) {
    // Invariant fields, like key and rule, can't be updated
    return new IssueDto()
      .setKee(issue.key())
      .setLine(issue.line())
      .setMessage(issue.message())
      .setEffortToFix(issue.effortToFix())
      .setDebt(issue.debtInMinutes())
      .setResolution(issue.resolution())
      .setStatus(issue.status())
      .setSeverity(issue.severity())
      .setChecksum(issue.checksum())
      .setManualSeverity(issue.manualSeverity())
      .setReporter(issue.reporter())
      .setAssignee(issue.assignee())
      .setActionPlanKey(issue.actionPlanKey())
      .setIssueAttributes(KeyValueFormat.format(issue.attributes()))
      .setAuthorLogin(issue.authorLogin())
      .setRuleKey(issue.ruleKey().repository(), issue.ruleKey().rule())
      .setComponentKey(issue.componentKey())
      .setRootComponentKey(issue.projectKey())
      .setRootComponentId(rootComponentId)
      .setIssueCreationDate(issue.creationDate())
      .setIssueCloseDate(issue.closeDate())
      .setIssueUpdateDate(issue.updateDate())
      .setSelectedAt(issue.selectedAt())
      .setUpdatedAt(now);
  }

  public DefaultIssue toDefaultIssue() {
    DefaultIssue issue = new DefaultIssue();
    issue.setKey(kee);
    issue.setStatus(status);
    issue.setResolution(resolution);
    issue.setMessage(message);
    issue.setEffortToFix(effortToFix);
    issue.setDebt(debt != null ? Duration.create(debt) : null);
    issue.setLine(line);
    issue.setSeverity(severity);
    issue.setReporter(reporter);
    issue.setAssignee(assignee);
    issue.setAttributes(KeyValueFormat.parse(Objects.firstNonNull(issueAttributes, "")));
    issue.setComponentKey(componentKey);
    issue.setComponentId(componentId);
    issue.setProjectKey(rootComponentKey);
    issue.setManualSeverity(manualSeverity);
    issue.setRuleKey(getRuleKey());
    issue.setLanguage(language);
    issue.setActionPlanKey(actionPlanKey);
    issue.setAuthorLogin(authorLogin);
    issue.setNew(false);
    issue.setCreationDate(issueCreationDate);
    issue.setCloseDate(issueCloseDate);
    issue.setUpdateDate(issueUpdateDate);
    issue.setSelectedAt(selectedAt);
    return issue;
  }

  public static IssueDto createFor(Project project, RuleDto rule) {
    return new IssueDto()
      .setRootComponentId(Long.valueOf(project.getId()))
      .setRuleId(rule.getId())
      .setKee(UUID.randomUUID().toString());
  }
}
