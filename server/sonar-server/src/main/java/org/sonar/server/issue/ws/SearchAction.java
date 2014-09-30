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
package org.sonar.server.issue.ws;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import org.sonar.api.i18n.I18n;
import org.sonar.api.issue.ActionPlan;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueComment;
import org.sonar.api.issue.IssueQuery;
import org.sonar.api.issue.internal.DefaultIssueComment;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.user.User;
import org.sonar.api.user.UserFinder;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.Duration;
import org.sonar.api.utils.Durations;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.issue.db.IssueChangeDao;
import org.sonar.core.persistence.DbSession;
import org.sonar.markdown.Markdown;
import org.sonar.server.db.DbClient;
import org.sonar.server.issue.IssueService;
import org.sonar.server.issue.actionplan.ActionPlanService;
import org.sonar.server.issue.filter.IssueFilterParameters;
import org.sonar.server.rule.Rule;
import org.sonar.server.rule.RuleService;
import org.sonar.server.search.QueryContext;
import org.sonar.server.search.Result;
import org.sonar.server.search.ws.SearchRequestHandler;
import org.sonar.server.user.UserSession;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

public class SearchAction extends SearchRequestHandler<IssueQuery, Issue> {

  public static final String SEARCH_ACTION = "search";

  private static final String ACTIONS_EXTRA_FIELD = "actions";
  private static final String TRANSITIONS_EXTRA_FIELD = "transitions";
  private static final String ASSIGNEE_NAME_EXTRA_FIELD = "assigneeName";
  private static final String REPORTER_NAME_EXTRA_FIELD = "reporterName";
  private static final String ACTION_PLAN_NAME_EXTRA_FIELD = "actionPlanName";

  private static final String EXTRA_FIELDS_PARAM = "extra_fields";

  private final IssueChangeDao issueChangeDao;
  private final IssueService service;
  private final IssueActionsWriter actionsWriter;

  private final RuleService ruleService;
  private final DbClient dbClient;
  private final ActionPlanService actionPlanService;
  private final UserFinder userFinder;
  private final I18n i18n;
  private final Durations durations;

  public SearchAction(DbClient dbClient, IssueChangeDao issueChangeDao, IssueService service, IssueActionsWriter actionsWriter, RuleService ruleService,
    ActionPlanService actionPlanService, UserFinder userFinder, I18n i18n, Durations durations) {
    super(SEARCH_ACTION);
    this.dbClient = dbClient;
    this.issueChangeDao = issueChangeDao;
    this.service = service;
    this.actionsWriter = actionsWriter;
    this.ruleService = ruleService;
    this.actionPlanService = actionPlanService;
    this.userFinder = userFinder;
    this.i18n = i18n;
    this.durations = durations;
  }

  @Override
  protected void doDefinition(WebService.NewAction action) {
    action.setDescription("Get a list of issues. If the number of issues is greater than 10,000, " +
      "only the first 10,000 ones are returned by the web service. " +
      "Requires Browse permission on project(s)")
      .setSince("3.6")
      .setResponseExample(Resources.getResource(this.getClass(), "example-search.json"));

    action.createParam(IssueFilterParameters.ISSUES)
      .setDescription("Comma-separated list of issue keys")
      .setExampleValue("5bccd6e8-f525-43a2-8d76-fcb13dde79ef");
    action.createParam(IssueFilterParameters.SEVERITIES)
      .setDescription("Comma-separated list of severities")
      .setExampleValue(Severity.BLOCKER + "," + Severity.CRITICAL)
      .setPossibleValues(Severity.ALL);
    action.createParam(IssueFilterParameters.STATUSES)
      .setDescription("Comma-separated list of statuses")
      .setExampleValue(Issue.STATUS_OPEN + "," + Issue.STATUS_REOPENED)
      .setPossibleValues(Issue.STATUSES);
    action.createParam(IssueFilterParameters.RESOLUTIONS)
      .setDescription("Comma-separated list of resolutions")
      .setExampleValue(Issue.RESOLUTION_FIXED + "," + Issue.RESOLUTION_REMOVED)
      .setPossibleValues(Issue.RESOLUTIONS);
    action.createParam(IssueFilterParameters.RESOLVED)
      .setDescription("To match resolved or unresolved issues")
      .setBooleanPossibleValues();
    action.createParam(IssueFilterParameters.COMPONENTS)
      .setDescription("To retrieve issues associated to a specific list of components (comma-separated list of component keys). " +
        "Note that if you set the value to a project key, only issues associated to this project are retrieved. " +
        "Issues associated to its sub-components (such as files, packages, etc.) are not retrieved. See also componentRoots")
      .setExampleValue("org.apache.struts:struts:org.apache.struts.Action");
    action.createParam(IssueFilterParameters.COMPONENT_ROOTS)
      .setDescription("To retrieve issues associated to a specific list of components and their sub-components (comma-separated list of component keys). " +
        "Views are not supported")
      .setExampleValue("org.apache.struts:struts");
    action.createParam(IssueFilterParameters.RULES)
      .setDescription("Comma-separated list of coding rule keys. Format is <repository>:<rule>")
      .setExampleValue("squid:AvoidCycles");
    action.createParam(IssueFilterParameters.HIDE_RULES)
      .setDescription("To not return rules")
      .setDefaultValue(false)
      .setBooleanPossibleValues();
    action.createParam(IssueFilterParameters.ACTION_PLANS)
      .setDescription("Comma-separated list of action plan keys (not names)")
      .setExampleValue("3f19de90-1521-4482-a737-a311758ff513");
    action.createParam(IssueFilterParameters.PLANNED)
      .setDescription("To retrieve issues associated to an action plan or not")
      .setBooleanPossibleValues();
    action.createParam(IssueFilterParameters.REPORTERS)
      .setDescription("Comma-separated list of reporter logins")
      .setExampleValue("admin");
    action.createParam(IssueFilterParameters.ASSIGNEES)
      .setDescription("Comma-separated list of assignee logins")
      .setExampleValue("admin,usera");
    action.createParam(IssueFilterParameters.ASSIGNED)
      .setDescription("To retrieve assigned or unassigned issues")
      .setBooleanPossibleValues();
    action.createParam(IssueFilterParameters.LANGUAGES)
      .setDescription("Comma-separated list of languages. Available since 4.4")
      .setExampleValue("java,js");
    action.createParam(EXTRA_FIELDS_PARAM)
      .setDescription("Add some extra fields on each issue. Available since 4.4")
      .setPossibleValues(ACTIONS_EXTRA_FIELD, TRANSITIONS_EXTRA_FIELD, ASSIGNEE_NAME_EXTRA_FIELD, REPORTER_NAME_EXTRA_FIELD, ACTION_PLAN_NAME_EXTRA_FIELD);
    action.createParam(IssueFilterParameters.CREATED_AT)
      .setDescription("To retrieve issues created at a given date. Format: date or datetime ISO formats")
      .setExampleValue("2013-05-01 (or 2013-05-01T13:00:00+0100)");
    action.createParam(IssueFilterParameters.CREATED_AFTER)
      .setDescription("To retrieve issues created after the given date (inclusive). Format: date or datetime ISO formats")
      .setExampleValue("2013-05-01 (or 2013-05-01T13:00:00+0100)");
    action.createParam(IssueFilterParameters.CREATED_BEFORE)
      .setDescription("To retrieve issues created before the given date (exclusive). Format: date or datetime ISO formats")
      .setExampleValue("2013-05-01 (or 2013-05-01T13:00:00+0100)");
    action.createParam(SearchRequestHandler.PARAM_SORT)
      .setDescription("Sort field")
      .setDeprecatedKey(IssueFilterParameters.SORT)
      .setPossibleValues(IssueQuery.SORTS);
    action.createParam(SearchRequestHandler.PARAM_ASCENDING)
      .setDeprecatedKey(IssueFilterParameters.ASC)
      .setDescription("Ascending sort")
      .setBooleanPossibleValues();
    action.createParam("format")
      .setDescription("Only json format is available. This parameter is kept only for backward compatibility and shouldn't be used anymore");
  }

  @Override
  protected IssueQuery doQuery(Request request) {
    IssueQuery.Builder builder = IssueQuery.builder()
      .requiredRole(UserRole.USER)
      .issueKeys(request.paramAsStrings(IssueFilterParameters.ISSUES))
      .severities(request.paramAsStrings(IssueFilterParameters.SEVERITIES))
      .statuses(request.paramAsStrings(IssueFilterParameters.STATUSES))
      .resolutions(request.paramAsStrings(IssueFilterParameters.RESOLUTIONS))
      .resolved(request.paramAsBoolean(IssueFilterParameters.RESOLVED))
      .components(request.paramAsStrings(IssueFilterParameters.COMPONENTS))
      .componentRoots(request.paramAsStrings(IssueFilterParameters.COMPONENT_ROOTS))
      .rules(stringsToRules(request.paramAsStrings(IssueFilterParameters.RULES)))
      .actionPlans(request.paramAsStrings(IssueFilterParameters.ACTION_PLANS))
      .reporters(request.paramAsStrings(IssueFilterParameters.REPORTERS))
      .assignees(request.paramAsStrings(IssueFilterParameters.ASSIGNEES))
      .languages(request.paramAsStrings(IssueFilterParameters.LANGUAGES))
      .assigned(request.paramAsBoolean(IssueFilterParameters.ASSIGNED))
      .planned(request.paramAsBoolean(IssueFilterParameters.PLANNED))
      .createdAt(request.paramAsDateTime(IssueFilterParameters.CREATED_AT))
      .createdAfter(request.paramAsDateTime(IssueFilterParameters.CREATED_AFTER))
      .createdBefore(request.paramAsDateTime(IssueFilterParameters.CREATED_BEFORE));
    String sort = request.param(SearchRequestHandler.PARAM_SORT);
    if (!Strings.isNullOrEmpty(sort)) {
      builder.sort(sort);
      builder.asc(request.paramAsBoolean(SearchRequestHandler.PARAM_ASCENDING));
    }
    return builder.build();
  }

  @Override
  protected Result<Issue> doSearch(IssueQuery query, QueryContext context) {
    Collection<String> components = query.components();
    if (components != null && components.size() == 1) {
      context.setShowFullResult(true);
    }
    return service.search(query, context);
  }

  @Override
  @CheckForNull
  protected Collection<String> possibleFields() {
    return Collections.emptyList();
  }

  @Override
  protected void doContextResponse(Request request, QueryContext context, Result<Issue> result, JsonWriter json) {
    List<String> issueKeys = newArrayList();
    Set<RuleKey> ruleKeys = newHashSet();
    Set<String> projectKeys = newHashSet();
    Set<String> componentKeys = newHashSet();
    Set<String> actionPlanKeys = newHashSet();
    List<String> userLogins = newArrayList();
    Map<String, User> usersByLogin = newHashMap();
    Multimap<String, DefaultIssueComment> commentsByIssues = ArrayListMultimap.create();

    for (Issue issue : result.getHits()) {
      issueKeys.add(issue.key());
      ruleKeys.add(issue.ruleKey());
      projectKeys.add(issue.projectKey());
      componentKeys.add(issue.componentKey());
      actionPlanKeys.add(issue.actionPlanKey());
      if (issue.reporter() != null) {
        userLogins.add(issue.reporter());
      }
      if (issue.assignee() != null) {
        userLogins.add(issue.assignee());
      }
    }

    DbSession session = dbClient.openSession(false);
    try {
      List<DefaultIssueComment> comments = issueChangeDao.selectCommentsByIssues(session, issueKeys);
      for (DefaultIssueComment issueComment : comments) {
        userLogins.add(issueComment.userLogin());
        commentsByIssues.put(issueComment.issueKey(), issueComment);
      }
      usersByLogin = getUsersByLogin(session, userLogins);

      List<ComponentDto> componentDtos = dbClient.componentDao().getByKeys(session, componentKeys);
      List<ComponentDto> subProjectDtos = dbClient.componentDao().findSubProjectsByComponentKeys(session, componentKeys);
      List<ComponentDto> projectDtos = dbClient.componentDao().getByKeys(session, projectKeys);

      componentDtos.addAll(subProjectDtos);
      componentDtos.addAll(projectDtos);
      writeProjects(json, projectDtos);
      writeComponents(json, componentDtos);
    } finally {
      session.close();
    }

    Map<String, ActionPlan> actionPlanByKeys = getActionPlanByKeys(actionPlanKeys);

    writeIssues(result, commentsByIssues, usersByLogin, actionPlanByKeys, request.paramAsStrings(EXTRA_FIELDS_PARAM), json);
    writeRules(json, !request.mandatoryParamAsBoolean(IssueFilterParameters.HIDE_RULES) ? ruleService.getByKeys(ruleKeys) : Collections.<Rule>emptyList());
    writeUsers(json, usersByLogin);
    writeActionPlans(json, actionPlanByKeys.values());

    // TODO remove legacy paging. Handled by the SearchRequestHandler
    writeLegacyPaging(context, json, result);
  }

  private void writeLegacyPaging(QueryContext context, JsonWriter json, Result<?> result) {
    // TODO remove with stas on HTML side
    json.prop("maxResultsReached", false);

    long pages = context.getLimit();
    if (pages > 0) {
      pages = result.getTotal() / context.getLimit();
      if (result.getTotal() % context.getLimit() > 0) {
        pages++;
      }
    }

    json.name("paging").beginObject()
      .prop("pageIndex", context.getPage())
      .prop("pageSize", context.getLimit())
      .prop("total", result.getTotal())
      // TODO Remove as part of Front-end rework on Issue Domain
      .prop("fTotal", i18n.formatInteger(UserSession.get().locale(), (int) result.getTotal()))
      .prop("pages", pages)
      .endObject();
  }

  // TODO change to use the RuleMapper
  private void writeRules(JsonWriter json, Collection<Rule> rules) {
    json.name("rules").beginArray();
    for (Rule rule : rules) {
      json.beginObject()
        .prop("key", rule.key().toString())
        .prop("name", rule.name())
        .prop("desc", rule.htmlDescription())
        .prop("status", rule.status().toString())
        .endObject();
    }
    json.endArray();
  }

  private void writeIssues(Result<Issue> result, Multimap<String, DefaultIssueComment> commentsByIssues, Map<String, User> usersByLogin, Map<String, ActionPlan> actionPlanByKeys,
                           @Nullable List<String> extraFields, JsonWriter json) {
    json.name("issues").beginArray();

    for (Issue issue : result.getHits()) {
      json.beginObject();

      String actionPlanKey = issue.actionPlanKey();
      Duration debt = issue.debt();
      Date updateDate = issue.updateDate();

      json
        .prop("key", issue.key())
        .prop("component", issue.componentKey())
        .prop("project", issue.projectKey())
        .prop("rule", issue.ruleKey().toString())
        .prop("status", issue.status())
        .prop("resolution", issue.resolution())
        .prop("severity", issue.severity())
        .prop("message", issue.message())
        .prop("line", issue.line())
        .prop("debt", debt != null ? durations.encode(debt) : null)
        .prop("reporter", issue.reporter())
        .prop("assignee", issue.assignee())
        .prop("author", issue.authorLogin())
        .prop("actionPlan", actionPlanKey)
        .prop("creationDate", isoDate(issue.creationDate()))
        .prop("updateDate", isoDate(updateDate))
        // TODO Remove as part of Front-end rework on Issue Domain
        .prop("fUpdateAge", formatAgeDate(updateDate))
        .prop("closeDate", isoDate(issue.closeDate()));

      writeIssueComments(commentsByIssues.get(issue.key()), usersByLogin, json);
      writeIssueAttributes(issue, json);
      writeIssueExtraFields(issue, usersByLogin, actionPlanByKeys, extraFields, json);
      json.endObject();
    }

    json.endArray();
  }

  private void writeIssueComments(Collection<DefaultIssueComment> issueComments, Map<String, User> usersByLogin, JsonWriter json) {
    if (!issueComments.isEmpty()) {
      json.name("comments").beginArray();
      String login = UserSession.get().login();
      for (IssueComment comment : issueComments) {
        String userLogin = comment.userLogin();
        User user = userLogin != null ? usersByLogin.get(userLogin) : null;
        json.beginObject()
          .prop("key", comment.key())
          .prop("login", comment.userLogin())
          .prop("userName", user != null ? user.name() : null)
          .prop("htmlText", Markdown.convertToHtml(comment.markdownText()))
          .prop("markdown", comment.markdownText())
          .prop("updatable", login != null && login.equals(userLogin))
          .prop("createdAt", DateUtils.formatDateTime(comment.createdAt()))
          .endObject();
      }
      json.endArray();
    }
  }

  private void writeIssueAttributes(Issue issue, JsonWriter json) {
    if (!issue.attributes().isEmpty()) {
      json.name("attr").beginObject();
      for (Map.Entry<String, String> entry : issue.attributes().entrySet()) {
        json.prop(entry.getKey(), entry.getValue());
      }
      json.endObject();
    }
  }

  private void writeIssueExtraFields(Issue issue, Map<String, User> usersByLogin, Map<String, ActionPlan> actionPlanByKeys, @Nullable List<String> extraFields, JsonWriter json) {
    if (extraFields != null && UserSession.get().isLoggedIn()) {
      if (extraFields.contains(ACTIONS_EXTRA_FIELD)) {
        actionsWriter.writeActions(issue, json);
      }

      if (extraFields.contains(TRANSITIONS_EXTRA_FIELD)) {
        actionsWriter.writeTransitions(issue, json);
      }

      String assignee = issue.assignee();
      if (extraFields.contains(ASSIGNEE_NAME_EXTRA_FIELD) && assignee != null) {
        User user = usersByLogin.get(assignee);
        json.prop(ASSIGNEE_NAME_EXTRA_FIELD, user != null ? user.name() : null);
      }

      String reporter = issue.reporter();
      if (extraFields.contains(REPORTER_NAME_EXTRA_FIELD) && reporter != null) {
        User user = usersByLogin.get(reporter);
        json.prop(REPORTER_NAME_EXTRA_FIELD, user != null ? user.name() : null);
      }

      String actionPlanKey = issue.actionPlanKey();
      if (extraFields.contains(ACTION_PLAN_NAME_EXTRA_FIELD) && actionPlanKey != null) {
        ActionPlan actionPlan = actionPlanByKeys.get(actionPlanKey);
        json.prop(ACTION_PLAN_NAME_EXTRA_FIELD, actionPlan != null ? actionPlan.name() : null);
      }
    }
  }

  private void writeComponents(JsonWriter json, List<ComponentDto> components) {
    json.name("components").beginArray();
    for (ComponentDto component : components) {
      json.beginObject()
        .prop("key", component.key())
        .prop("id", component.getId())
        .prop("qualifier", component.qualifier())
        .prop("name", component.name())
        .prop("longName", component.longName())
        .prop("path", component.path())
        // On a root project, subProjectId is null but projectId is equal to itself, which make no sense.
        .prop("projectId", (component.projectId() != null && component.subProjectId() != null) ? component.projectId() : null)
        .prop("subProjectId", component.subProjectId())
        .endObject();
    }
    json.endArray();
  }

  private void writeProjects(JsonWriter json, List<ComponentDto> projects) {
    json.name("projects").beginArray();
    for (ComponentDto project : projects) {
      json.beginObject()
        .prop("key", project.key())
        .prop("id", project.getId())
        .prop("qualifier", project.qualifier())
        .prop("name", project.name())
        .prop("longName", project.longName())
        .endObject();
    }
    json.endArray();
  }

  private void writeUsers(JsonWriter json, Map<String, User> usersByLogin) {
    json.name("users").beginArray();
    for (User user : usersByLogin.values()) {
      json.beginObject()
        .prop("login", user.login())
        .prop("name", user.name())
        .prop("active", user.active())
        .prop("email", user.email())
        .endObject();
    }
    json.endArray();
  }

  private void writeActionPlans(JsonWriter json, Collection<ActionPlan> plans) {
    if (!plans.isEmpty()) {
      json.name("actionPlans").beginArray();
      for (ActionPlan actionPlan : plans) {
        Date deadLine = actionPlan.deadLine();
        Date updatedAt = actionPlan.updatedAt();

        json.beginObject()
          .prop("key", actionPlan.key())
          .prop("name", actionPlan.name())
          .prop("status", actionPlan.status())
          .prop("project", actionPlan.projectKey())
          .prop("userLogin", actionPlan.userLogin())
          .prop("deadLine", isoDate(deadLine))
          .prop("fDeadLine", formatDate(deadLine))
          .prop("createdAt", isoDate(actionPlan.createdAt()))
          .prop("fCreatedAt", formatDate(actionPlan.createdAt()))
          .prop("updatedAt", isoDate(actionPlan.updatedAt()))
          .prop("fUpdatedAt", formatDate(updatedAt))
          .endObject();
      }
      json.endArray();
    }
  }

  private Map<String, User> getUsersByLogin(DbSession session, List<String> userLogins) {
    Map<String, User> usersByLogin = newHashMap();
    for (User user : userFinder.findByLogins(userLogins)) {
      usersByLogin.put(user.login(), user);
    }
    return usersByLogin;
  }

  private Map<String, ActionPlan> getActionPlanByKeys(Collection<String> actionPlanKeys) {
    Map<String, ActionPlan> actionPlans = newHashMap();
    for (ActionPlan actionPlan : actionPlanService.findByKeys(actionPlanKeys)) {
      actionPlans.put(actionPlan.key(), actionPlan);
    }
    return actionPlans;
  }

  @CheckForNull
  private String isoDate(@Nullable Date date) {
    if (date != null) {
      return DateUtils.formatDateTime(date);
    }
    return null;
  }

  @CheckForNull
  private String formatDate(@Nullable Date date) {
    if (date != null) {
      return i18n.formatDateTime(UserSession.get().locale(), date);
    }
    return null;
  }

  @CheckForNull
  private String formatAgeDate(@Nullable Date date) {
    if (date != null) {
      return i18n.ageFromNow(UserSession.get().locale(), date);
    }
    return null;
  }

  @CheckForNull
  private static Collection<RuleKey> stringsToRules(@Nullable Collection<String> rules) {
    if (rules != null) {
      return newArrayList(Iterables.transform(rules, new Function<String, RuleKey>() {
        @Override
        public RuleKey apply(@Nullable String s) {
          return s != null ? RuleKey.parse(s) : null;
        }
      }));
    }
    return null;
  }
}
