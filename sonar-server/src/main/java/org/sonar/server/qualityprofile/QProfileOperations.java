/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
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

package org.sonar.server.qualityprofile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.ibatis.session.SqlSession;
import org.sonar.api.PropertyType;
import org.sonar.api.ServerComponent;
import org.sonar.api.profiles.ProfileImporter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.check.Cardinality;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.preview.PreviewCache;
import org.sonar.core.properties.PropertiesDao;
import org.sonar.core.properties.PropertyDto;
import org.sonar.core.qualityprofile.db.*;
import org.sonar.core.rule.RuleDao;
import org.sonar.core.rule.RuleDto;
import org.sonar.core.rule.RuleParamDto;
import org.sonar.server.configuration.ProfilesManager;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.rule.RuleRegistry;
import org.sonar.server.user.UserSession;

import java.io.StringReader;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

public class QProfileOperations implements ServerComponent {

  private static final String PROPERTY_PREFIX = "sonar.profile.";

  private final MyBatis myBatis;
  private final QualityProfileDao dao;
  private final ActiveRuleDao activeRuleDao;
  private final RuleDao ruleDao;
  private final PropertiesDao propertiesDao;
  private final List<ProfileImporter> importers;
  private final PreviewCache dryRunCache;
  private final RuleRegistry ruleRegistry;
  private final ProfilesManager profilesManager;

  private final System2 system;

  /**
   * Used by pico when no plugin provide profile exporter / importer
   */
  public QProfileOperations(MyBatis myBatis, QualityProfileDao dao, ActiveRuleDao activeRuleDao, RuleDao ruleDao, PropertiesDao propertiesDao,
                            PreviewCache dryRunCache, RuleRegistry ruleRegistry, ProfilesManager profilesManager) {
    this(myBatis, dao, activeRuleDao, ruleDao, propertiesDao, Lists.<ProfileImporter>newArrayList(), dryRunCache, ruleRegistry,
      profilesManager, System2.INSTANCE);
  }

  public QProfileOperations(MyBatis myBatis, QualityProfileDao dao, ActiveRuleDao activeRuleDao, RuleDao ruleDao, PropertiesDao propertiesDao,
                            List<ProfileImporter> importers, PreviewCache dryRunCache, RuleRegistry ruleRegistry, ProfilesManager profilesManager) {
    this(myBatis, dao, activeRuleDao, ruleDao, propertiesDao, importers, dryRunCache, ruleRegistry,
      profilesManager, System2.INSTANCE);
  }

  @VisibleForTesting
  QProfileOperations(MyBatis myBatis, QualityProfileDao dao, ActiveRuleDao activeRuleDao, RuleDao ruleDao, PropertiesDao propertiesDao,
                     List<ProfileImporter> importers, PreviewCache dryRunCache, RuleRegistry ruleRegistry,
                     ProfilesManager profilesManager, System2 system) {
    this.myBatis = myBatis;
    this.dao = dao;
    this.activeRuleDao = activeRuleDao;
    this.ruleDao = ruleDao;
    this.propertiesDao = propertiesDao;
    this.importers = importers;
    this.dryRunCache = dryRunCache;
    this.ruleRegistry = ruleRegistry;
    this.profilesManager = profilesManager;
    this.system = system;
  }

  public NewProfileResult newProfile(String name, String language, Map<String, String> xmlProfilesByPlugin, UserSession userSession) {
    checkPermission(userSession);

    NewProfileResult result = new NewProfileResult();
    List<RulesProfile> importProfiles = readProfilesFromXml(result, xmlProfilesByPlugin);

    SqlSession session = myBatis.openSession();
    try {
      QualityProfileDto dto = new QualityProfileDto().setName(name).setLanguage(language).setVersion(1).setUsed(false);
      dao.insert(dto, session);
      for (RulesProfile rulesProfile : importProfiles) {
        importProfile(dto, rulesProfile, session);
      }
      result.setProfile(QProfile.from(dto));
      session.commit();
      dryRunCache.reportGlobalModification();
    } finally {
      MyBatis.closeQuietly(session);
    }
    return result;
  }

  public void renameProfile(QualityProfileDto qualityProfile, String newName, UserSession userSession) {
    checkPermission(userSession);
    qualityProfile.setName(newName);
    dao.update(qualityProfile);
  }

  public void setDefaultProfile(QualityProfileDto qualityProfile, UserSession userSession) {
    checkPermission(userSession);
    propertiesDao.setProperty(new PropertyDto().setKey(PROPERTY_PREFIX + qualityProfile.getLanguage()).setValue(qualityProfile.getName()));
  }

  public ActiveRuleDto createActiveRule(QualityProfileDto qualityProfile, RuleDto rule, String severity, UserSession userSession) {
    checkPermission(userSession);
    checkSeverity(severity);
    SqlSession session = myBatis.openSession();
    try {
      ActiveRuleDto activeRule = new ActiveRuleDto()
        .setProfileId(qualityProfile.getId())
        .setRuleId(rule.getId())
        .setSeverity(Severity.ordinal(severity));
      activeRuleDao.insert(activeRule, session);

      List<RuleParamDto> ruleParams = ruleDao.selectParameters(rule.getId(), session);
      List<ActiveRuleParamDto> activeRuleParams = Lists.newArrayList();
      for (RuleParamDto ruleParam : ruleParams) {
        ActiveRuleParamDto activeRuleParam = new ActiveRuleParamDto()
          .setActiveRuleId(activeRule.getId())
          .setRulesParameterId(ruleParam.getId())
          .setKey(ruleParam.getName())
          .setValue(ruleParam.getDefaultValue());
        activeRuleParams.add(activeRuleParam);
        activeRuleDao.insert(activeRuleParam, session);
      }
      session.commit();

      RuleInheritanceActions actions = profilesManager.activated(qualityProfile.getId(), activeRule.getId(), userSession.name());
      reindexInheritanceResult(actions, session);

      return activeRule;
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void updateSeverity(ActiveRuleDto activeRule, String newSeverity, UserSession userSession) {
    checkPermission(userSession);
    checkSeverity(newSeverity);
    SqlSession session = myBatis.openSession();
    try {
      Integer oldSeverity = activeRule.getSeverity();
      activeRule.setSeverity(Severity.ordinal(newSeverity));
      activeRuleDao.update(activeRule, session);
      session.commit();

      RuleInheritanceActions actions = profilesManager.ruleSeverityChanged(activeRule.getProfileId(), activeRule.getId(),
        RulePriority.valueOfInt(oldSeverity), RulePriority.valueOf(newSeverity),
        userSession.name());
      reindexInheritanceResult(actions, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deactivateRule(ActiveRuleDto activeRule, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      RuleInheritanceActions actions = profilesManager.deactivated(activeRule.getProfileId(), activeRule.getId(), userSession.name());

      activeRuleDao.deleteParameters(activeRule.getId(), session);
      activeRuleDao.delete(activeRule.getId(), session);
      actions.addToDelete(activeRule.getId());
      session.commit();

      reindexInheritanceResult(actions, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void createActiveRuleParam(ActiveRuleDto activeRule, String key, String value, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      RuleParamDto ruleParam = findRuleParamNotNull(activeRule.getRulId(), key, session);
      validateParam(ruleParam.getType(), value);
      ActiveRuleParamDto activeRuleParam = new ActiveRuleParamDto().setActiveRuleId(activeRule.getId()).setKey(key).setValue(value).setRulesParameterId(ruleParam.getId());
      activeRuleDao.insert(activeRuleParam, session);
      session.commit();

      RuleInheritanceActions actions = profilesManager.ruleParamChanged(activeRule.getProfileId(), activeRule.getId(), key, null, value, userSession.name());
      reindexInheritanceResult(actions, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteActiveRuleParam(ActiveRuleDto activeRule, ActiveRuleParamDto activeRuleParam, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      activeRuleDao.deleteParameter(activeRuleParam.getId(), session);
      session.commit();

      RuleInheritanceActions actions = profilesManager.ruleParamChanged(activeRule.getProfileId(), activeRule.getId(), activeRuleParam.getKey(), activeRuleParam.getValue(),
        null, userSession.name());
      reindexInheritanceResult(actions, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void updateActiveRuleParam(ActiveRuleDto activeRule, ActiveRuleParamDto activeRuleParam, String value, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      RuleParamDto ruleParam = findRuleParamNotNull(activeRule.getRulId(), activeRuleParam.getKey(), session);
      validateParam(ruleParam.getType(), value);

      String sanitizedValue = Strings.emptyToNull(value);
      String oldValue = activeRuleParam.getValue();
      activeRuleParam.setValue(sanitizedValue);
      activeRuleDao.update(activeRuleParam, session);
      session.commit();

      RuleInheritanceActions actions = profilesManager.ruleParamChanged(activeRule.getProfileId(), activeRule.getId(), activeRuleParam.getKey(), oldValue,
        sanitizedValue, getLoggedName(userSession));
      reindexInheritanceResult(actions, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void updateActiveRuleNote(ActiveRuleDto activeRule, String note, UserSession userSession) {
    checkPermission(userSession);
    Date now = new Date(system.now());
    SqlSession session = myBatis.openSession();
    try {
      if (activeRule.getNoteData() == null) {
        activeRule.setNoteCreatedAt(now);
        activeRule.setNoteUserLogin(userSession.login());
      }
      activeRule.setNoteUpdatedAt(now);
      activeRule.setNoteData(note);
      activeRuleDao.update(activeRule, session);
      session.commit();

      reindexActiveRule(activeRule, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteActiveRuleNote(ActiveRuleDto activeRule, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      activeRule.setNoteData(null);
      activeRule.setNoteUserLogin(null);
      activeRule.setNoteCreatedAt(null);
      activeRule.setNoteUpdatedAt(null);
      activeRuleDao.update(activeRule);
      session.commit();

      reindexActiveRule(activeRule, session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void updateRuleNote(RuleDto rule, String note, UserSession userSession) {
    checkPermission(userSession);
    Date now = new Date(system.now());

    SqlSession session = myBatis.openSession();
    try {
      if (rule.getNoteData() == null) {
        rule.setNoteCreatedAt(now);
        rule.setNoteUserLogin(getLoggedLogin(userSession));
      }
      rule.setNoteUpdatedAt(now);
      rule.setNoteData(note);

      // TODO should we update rule.updatedAt ???

      ruleDao.update(rule);
      session.commit();

      // TODO notify E/S of rule change
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteRuleNote(RuleDto rule, UserSession userSession) {
    checkPermission(userSession);

    SqlSession session = myBatis.openSession();
    try {
      rule.setNoteData(null);
      rule.setNoteUserLogin(null);
      rule.setNoteCreatedAt(null);
      rule.setNoteUpdatedAt(null);
      // TODO also update rule.updatedAt ?
      ruleDao.update(rule);
      session.commit();

      // TODO notify E/S of rule change
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public RuleDto createRule(RuleDto templateRule, String name, String severity, String description, Map<String, String> paramsByKey,
                            UserSession userSession) {
    checkPermission(userSession);
    SqlSession session = myBatis.openSession();
    try {
      RuleDto rule = new RuleDto()
        .setParentId(templateRule.getId())
        .setName(name)
        .setDescription(description)
        .setSeverity(Severity.ordinal(severity))
        .setRepositoryKey(templateRule.getRepositoryKey())
        .setConfigKey(templateRule.getConfigKey())
        .setRuleKey(templateRule.getRuleKey() + "_" + system.now())
        .setCardinality(Cardinality.SINGLE)
        .setStatus(Rule.STATUS_READY)
        .setLanguage(templateRule.getLanguage())
        .setCreatedAt(new Date(system.now()))
        .setUpdatedAt(new Date(system.now()));
      ruleDao.insert(rule, session);

      List<RuleParamDto> templateRuleParams = ruleDao.selectParameters(templateRule.getId(), session);
      List<RuleParamDto> ruleParams = newArrayList();
      for (RuleParamDto templateRuleParam : templateRuleParams) {
        String key = templateRuleParam.getName();
        String value = paramsByKey.get(key);

        RuleParamDto param = new RuleParamDto()
          .setRuleId(rule.getId())
          .setName(key)
          .setDescription(templateRuleParam.getDescription())
          .setType(templateRuleParam.getType())
          .setDefaultValue(Strings.emptyToNull(value));
        ruleDao.insert(param, session);
        ruleParams.add(param);
      }
      session.commit();
      ruleRegistry.save(rule, ruleParams);

      return rule;
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void updateRule(RuleDto rule, String name, String severity, String description, Map<String, String> paramsByKey,
                         UserSession userSession) {
    checkPermission(userSession);
    SqlSession session = myBatis.openSession();
    try {
      rule.setName(name)
        .setDescription(description)
        .setSeverity(Severity.ordinal(severity))
        .setUpdatedAt(new Date(system.now()));
      ruleDao.update(rule, session);

      List<RuleParamDto> ruleParams = ruleDao.selectParameters(rule.getId(), session);
      for (RuleParamDto ruleParam : ruleParams) {
        String value = paramsByKey.get(ruleParam.getName());
        ruleParam.setDefaultValue(Strings.emptyToNull(value));
        ruleDao.update(ruleParam, session);
      }
      session.commit();
      ruleRegistry.save(rule, ruleParams);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteRule(RuleDto rule, UserSession userSession) {
    checkPermission(userSession);
    SqlSession session = myBatis.openSession();
    try {
      // Set status REMOVED on rule
      rule.setStatus(Rule.STATUS_REMOVED)
        .setUpdatedAt(new Date(system.now()));
      ruleDao.update(rule, session);
      session.commit();
      ruleRegistry.save(rule, ruleDao.selectParameters(rule.getId(), session));

      // Delete all active rules and active rule params linked to the rule
      List<ActiveRuleDto> activeRules = activeRuleDao.selectByRuleId(rule.getId());
      for (ActiveRuleDto activeRule : activeRules) {
        activeRuleDao.deleteParameters(activeRule.getId(), session);
      }
      activeRuleDao.deleteFromRule(rule.getId(), session);
      session.commit();
      ruleRegistry.deleteActiveRules(newArrayList(Iterables.transform(activeRules, new Function<ActiveRuleDto, Integer>() {
        @Override
        public Integer apply(ActiveRuleDto input) {
          return input.getId();
        }
      })));
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  private void reindexInheritanceResult(RuleInheritanceActions actions, SqlSession session) {
    ruleRegistry.deleteActiveRules(actions.idsToDelete());
    List<ActiveRuleDto> activeRules = activeRuleDao.selectByIds(actions.idsToIndex(), session);
    Multimap<Integer, ActiveRuleParamDto> paramsByActiveRule = ArrayListMultimap.create();
    for (ActiveRuleParamDto param : activeRuleDao.selectParamsByActiveRuleIds(actions.idsToIndex(), session)) {
      paramsByActiveRule.put(param.getActiveRuleId(), param);
    }
    ruleRegistry.bulkIndexActiveRules(activeRules, paramsByActiveRule);
  }

  private void reindexActiveRule(ActiveRuleDto activeRuleDto, SqlSession session) {
    ruleRegistry.deleteActiveRules(newArrayList(activeRuleDto.getId()));
    Multimap<Integer, ActiveRuleParamDto> paramsByActiveRule = ArrayListMultimap.create();
    for (ActiveRuleParamDto param : activeRuleDao.selectParamsByActiveRuleId(activeRuleDto.getId(), session)) {
      paramsByActiveRule.put(param.getActiveRuleId(), param);
    }
    ruleRegistry.bulkIndexActiveRules(newArrayList(activeRuleDto), paramsByActiveRule);
  }

  private List<RulesProfile> readProfilesFromXml(NewProfileResult result, Map<String, String> xmlProfilesByPlugin) {
    List<RulesProfile> profiles = newArrayList();
    ValidationMessages messages = ValidationMessages.create();
    for (Map.Entry<String, String> entry : xmlProfilesByPlugin.entrySet()) {
      String pluginKey = entry.getKey();
      String file = entry.getValue();
      ProfileImporter importer = getProfileImporter(pluginKey);
      RulesProfile profile = importer.importProfile(new StringReader(file), messages);
      processValidationMessages(messages, result);
      profiles.add(profile);
    }
    return profiles;
  }

  private void importProfile(QualityProfileDto qualityProfileDto, RulesProfile rulesProfile, SqlSession sqlSession) {
    List<ActiveRuleDto> activeRuleDtos = newArrayList();
    Multimap<Integer, ActiveRuleParamDto> paramsByActiveRule = ArrayListMultimap.create();
    for (ActiveRule activeRule : rulesProfile.getActiveRules()) {
      ActiveRuleDto activeRuleDto = toActiveRuleDto(activeRule, qualityProfileDto);
      activeRuleDao.insert(activeRuleDto, sqlSession);
      activeRuleDtos.add(activeRuleDto);
      for (ActiveRuleParam activeRuleParam : activeRule.getActiveRuleParams()) {
        ActiveRuleParamDto activeRuleParamDto = toActiveRuleParamDto(activeRuleParam, activeRuleDto);
        activeRuleDao.insert(activeRuleParamDto, sqlSession);
        paramsByActiveRule.put(activeRuleDto.getId(), activeRuleParamDto);
      }
    }
    ruleRegistry.bulkIndexActiveRules(activeRuleDtos, paramsByActiveRule);
  }

  private ProfileImporter getProfileImporter(String exporterKey) {
    for (ProfileImporter importer : importers) {
      if (StringUtils.equals(exporterKey, importer.getKey())) {
        return importer;
      }
    }
    return null;
  }

  private void processValidationMessages(ValidationMessages messages, NewProfileResult result) {
    if (!messages.getErrors().isEmpty()) {
      List<BadRequestException.Message> errors = newArrayList();
      for (String error : messages.getErrors()) {
        errors.add(BadRequestException.Message.of(error));
      }
      throw BadRequestException.of("Fail to create profile", errors);
    }
    result.setWarnings(messages.getWarnings());
    result.setInfos(messages.getInfos());
  }

  private ActiveRuleDto toActiveRuleDto(ActiveRule activeRule, QualityProfileDto dto) {
    return new ActiveRuleDto()
      .setProfileId(dto.getId())
      .setRuleId(activeRule.getRule().getId())
      .setSeverity(toSeverityLevel(activeRule.getSeverity()));
  }

  private Integer toSeverityLevel(RulePriority rulePriority) {
    return rulePriority.ordinal();
  }

  private ActiveRuleParamDto toActiveRuleParamDto(ActiveRuleParam activeRuleParam, ActiveRuleDto activeRuleDto) {
    return new ActiveRuleParamDto()
      .setActiveRuleId(activeRuleDto.getId())
      .setRulesParameterId(activeRuleParam.getRuleParam().getId())
      .setKey(activeRuleParam.getKey())
      .setValue(activeRuleParam.getValue());
  }

  private void checkPermission(UserSession userSession) {
    userSession.checkLoggedIn();
    userSession.checkGlobalPermission(GlobalPermissions.QUALITY_PROFILE_ADMIN);
  }

  private String getLoggedName(UserSession userSession) {
    String name = userSession.name();
    if (Strings.isNullOrEmpty(name)) {
      throw new BadRequestException("User name can't be null");
    }
    return name;
  }

  private String getLoggedLogin(UserSession userSession) {
    String login = userSession.login();
    if (Strings.isNullOrEmpty(login)) {
      throw new BadRequestException("User login can't be null");
    }
    return login;
  }

  private void checkSeverity(String severity) {
    if (!Severity.ALL.contains(severity)) {
      throw new BadRequestException("The severity is not valid");
    }
  }

  private RuleParamDto findRuleParamNotNull(Integer ruleId, String key, SqlSession session) {
    RuleParamDto ruleParam = ruleDao.selectParamByRuleAndKey(ruleId, key, session);
    if (ruleParam == null) {
      throw new IllegalArgumentException("No rule param found");
    }
    return ruleParam;
  }

  private void validateParam(String type, String value) {
    if (type.equals(PropertyType.INTEGER.name()) && !NumberUtils.isDigits(value)) {
      throw new BadRequestException(String.format("Value '%s' must be an integer.", value));
    }
  }

}
