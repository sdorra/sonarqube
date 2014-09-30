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
package org.sonar.server.platform;

import com.google.common.collect.Lists;
import org.sonar.api.config.EmailSettings;
import org.sonar.api.issue.action.Actions;
import org.sonar.api.platform.ComponentContainer;
import org.sonar.api.profiles.AnnotationProfileParser;
import org.sonar.api.profiles.XMLProfileParser;
import org.sonar.api.profiles.XMLProfileSerializer;
import org.sonar.api.resources.Languages;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.rules.AnnotationRuleParser;
import org.sonar.api.rules.XMLRuleParser;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonar.api.utils.Durations;
import org.sonar.api.utils.HttpDownloader;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.internal.TempFolderCleaner;
import org.sonar.core.component.SnapshotPerspectives;
import org.sonar.core.config.CorePropertyDefinitions;
import org.sonar.core.config.Logback;
import org.sonar.core.i18n.DefaultI18n;
import org.sonar.core.i18n.GwtI18n;
import org.sonar.core.i18n.RuleI18nManager;
import org.sonar.core.issue.IssueFilterSerializer;
import org.sonar.core.issue.IssueNotifications;
import org.sonar.core.issue.IssueUpdater;
import org.sonar.core.issue.workflow.FunctionExecutor;
import org.sonar.core.issue.workflow.IssueWorkflow;
import org.sonar.core.measure.db.MeasureFilterDao;
import org.sonar.core.metric.DefaultMetricFinder;
import org.sonar.core.notification.DefaultNotificationManager;
import org.sonar.core.permission.PermissionFacade;
import org.sonar.core.persistence.*;
import org.sonar.core.preview.PreviewCache;
import org.sonar.core.profiling.Profiling;
import org.sonar.core.purge.PurgeProfiler;
import org.sonar.core.qualitygate.db.ProjectQgateAssociationDao;
import org.sonar.core.qualitygate.db.QualityGateConditionDao;
import org.sonar.core.qualitygate.db.QualityGateDao;
import org.sonar.core.resource.DefaultResourcePermissions;
import org.sonar.core.test.TestPlanPerspectiveLoader;
import org.sonar.core.test.TestablePerspectiveLoader;
import org.sonar.core.timemachine.Periods;
import org.sonar.core.user.DefaultUserFinder;
import org.sonar.core.user.HibernateUserFinder;
import org.sonar.jpa.dao.MeasuresDao;
import org.sonar.jpa.session.DatabaseSessionFactory;
import org.sonar.jpa.session.DatabaseSessionProvider;
import org.sonar.jpa.session.DefaultDatabaseConnector;
import org.sonar.jpa.session.ThreadLocalDatabaseSessionFactory;
import org.sonar.server.activity.ActivityService;
import org.sonar.server.activity.RubyQProfileActivityService;
import org.sonar.server.activity.db.ActivityDao;
import org.sonar.server.activity.index.ActivityIndex;
import org.sonar.server.activity.index.ActivityNormalizer;
import org.sonar.server.activity.ws.ActivitiesWebService;
import org.sonar.server.activity.ws.ActivityMapping;
import org.sonar.server.authentication.ws.AuthenticationWs;
import org.sonar.server.batch.*;
import org.sonar.server.charts.ChartFactory;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.component.DefaultComponentFinder;
import org.sonar.server.component.DefaultRubyComponentService;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.component.db.SnapshotDao;
import org.sonar.server.component.ws.*;
import org.sonar.server.computation.AnalysisReportTasksCleaner;
import org.sonar.server.computation.ComputationService;
import org.sonar.server.computation.db.AnalysisReportDao;
import org.sonar.server.config.ws.PropertiesWs;
import org.sonar.server.dashboard.db.DashboardDao;
import org.sonar.server.dashboard.db.WidgetDao;
import org.sonar.server.dashboard.db.WidgetPropertyDao;
import org.sonar.server.dashboard.ws.DashboardsShowAction;
import org.sonar.server.dashboard.ws.DashboardsWebService;
import org.sonar.server.db.DatabaseChecker;
import org.sonar.server.db.DbClient;
import org.sonar.server.db.EmbeddedDatabaseFactory;
import org.sonar.server.db.migrations.DatabaseMigrations;
import org.sonar.server.db.migrations.DatabaseMigrator;
import org.sonar.server.debt.*;
import org.sonar.server.design.FileDesignWidget;
import org.sonar.server.design.PackageDesignWidget;
import org.sonar.server.duplication.ws.DuplicationsJsonWriter;
import org.sonar.server.duplication.ws.DuplicationsParser;
import org.sonar.server.duplication.ws.DuplicationsWs;
import org.sonar.server.issue.*;
import org.sonar.server.issue.actionplan.ActionPlanService;
import org.sonar.server.issue.actionplan.ActionPlanWs;
import org.sonar.server.issue.db.IssueAuthorizationDao;
import org.sonar.server.issue.db.IssueDao;
import org.sonar.server.issue.filter.IssueFilterService;
import org.sonar.server.issue.filter.IssueFilterWriter;
import org.sonar.server.issue.filter.IssueFilterWs;
import org.sonar.server.issue.index.IssueAuthorizationIndex;
import org.sonar.server.issue.index.IssueAuthorizationNormalizer;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.issue.index.IssueNormalizer;
import org.sonar.server.issue.ws.IssueActionsWriter;
import org.sonar.server.issue.ws.IssueShowAction;
import org.sonar.server.issue.ws.IssuesWs;
import org.sonar.server.measure.MeasureFilterEngine;
import org.sonar.server.measure.MeasureFilterExecutor;
import org.sonar.server.measure.MeasureFilterFactory;
import org.sonar.server.measure.persistence.MeasureDao;
import org.sonar.server.measure.persistence.MetricDao;
import org.sonar.server.measure.ws.ManualMeasuresWs;
import org.sonar.server.measure.ws.MetricsWs;
import org.sonar.server.measure.ws.TimeMachineWs;
import org.sonar.server.notifications.NotificationCenter;
import org.sonar.server.notifications.NotificationService;
import org.sonar.server.permission.InternalPermissionService;
import org.sonar.server.permission.InternalPermissionTemplateService;
import org.sonar.server.permission.PermissionFinder;
import org.sonar.server.permission.ws.PermissionsWs;
import org.sonar.server.platform.ws.L10nWs;
import org.sonar.server.platform.ws.RestartHandler;
import org.sonar.server.platform.ws.ServerWs;
import org.sonar.server.platform.ws.SystemWs;
import org.sonar.server.plugins.*;
import org.sonar.server.qualitygate.QgateProjectFinder;
import org.sonar.server.qualitygate.QualityGates;
import org.sonar.server.qualitygate.RegisterQualityGates;
import org.sonar.server.qualitygate.ws.*;
import org.sonar.server.qualityprofile.*;
import org.sonar.server.qualityprofile.db.ActiveRuleDao;
import org.sonar.server.qualityprofile.index.ActiveRuleIndex;
import org.sonar.server.qualityprofile.index.ActiveRuleNormalizer;
import org.sonar.server.qualityprofile.ws.*;
import org.sonar.server.rule.*;
import org.sonar.server.rule.db.RuleDao;
import org.sonar.server.rule.index.RuleIndex;
import org.sonar.server.rule.index.RuleNormalizer;
import org.sonar.server.rule.ws.*;
import org.sonar.server.search.*;
import org.sonar.server.source.CodeColorizers;
import org.sonar.server.source.DeprecatedSourceDecorator;
import org.sonar.server.source.HtmlSourceDecorator;
import org.sonar.server.source.SourceService;
import org.sonar.server.source.ws.ScmAction;
import org.sonar.server.source.ws.ScmWriter;
import org.sonar.server.source.ws.ShowAction;
import org.sonar.server.source.ws.SourcesWs;
import org.sonar.server.startup.*;
import org.sonar.server.test.CoverageService;
import org.sonar.server.test.ws.*;
import org.sonar.server.text.MacroInterpreter;
import org.sonar.server.text.RubyTextService;
import org.sonar.server.ui.JRubyI18n;
import org.sonar.server.ui.JRubyProfiling;
import org.sonar.server.ui.PageDecorations;
import org.sonar.server.ui.Views;
import org.sonar.server.updatecenter.ws.UpdateCenterWs;
import org.sonar.server.user.*;
import org.sonar.server.user.db.GroupDao;
import org.sonar.server.user.ws.FavoritesWs;
import org.sonar.server.user.ws.UserPropertiesWs;
import org.sonar.server.user.ws.UsersWs;
import org.sonar.server.util.*;
import org.sonar.server.ws.ListingWs;
import org.sonar.server.ws.WebServiceEngine;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

class ServerComponents {

  private final Object[] rootComponents;
  private final Properties properties;
  private List level4AddedComponents = Lists.newArrayList();

  ServerComponents(Platform platform, Properties properties, Object... rootComponents) {
    this.properties = properties;
    this.rootComponents = Lists.newArrayList(properties, platform, rootComponents)
      .toArray(new Object[rootComponents.length + 2]);
  }

  /**
   * All the stuff required to connect to database
   */
  Collection level1Components() {
    List components = Lists.newArrayList(rootComponents);
    components.addAll(Arrays.asList(
      ServerSettings.class,
      ServerImpl.class,
      Logback.class,
      Profiling.class,
      JRubyProfiling.class,
      EmbeddedDatabaseFactory.class,
      DefaultDatabase.class,
      DatabaseChecker.class,
      MyBatis.class,
      IndexQueue.class,
      DatabaseServerCompatibility.class,
      DatabaseVersion.class,
      PurgeProfiler.class,
      DefaultServerFileSystem.class,
      PreviewDatabaseFactory.class,
      SemaphoreUpdater.class,
      SemaphoresImpl.class,
      TempFolderCleaner.class,
      new TempFolderProvider(),
      System2.INSTANCE,

      // DB
      MeasureDao.class,
      MetricDao.class,
      ComponentDao.class,
      SnapshotDao.class,
      DbClient.class,
      MeasureFilterDao.class,
      AnalysisReportDao.class,

      // Elasticsearch
      SearchClient.class,
      IndexClient.class,
      SearchHealth.class,

      // users
      GroupDao.class,

      // dashboards
      DashboardDao.class,
      DashboardsWebService.class,
      DashboardsShowAction.class,
      WidgetDao.class,
      WidgetPropertyDao.class,

      // rules/qprofiles
      RuleNormalizer.class,
      ActiveRuleNormalizer.class,
      RuleIndex.class,
      ActiveRuleIndex.class,
      RuleDao.class,
      ActiveRuleDao.class,

      // issues
      IssueNormalizer.class,
      IssueAuthorizationNormalizer.class,
      IssueIndex.class,
      IssueAuthorizationIndex.class,
      IssueDao.class,
      IssueAuthorizationDao.class,

      // Activity
      ActivityService.class,
      ActivityNormalizer.class,
      ActivityIndex.class,
      ActivityDao.class
      ));
    components.addAll(CorePropertyDefinitions.all());
    components.addAll(DatabaseMigrations.CLASSES);
    components.addAll(DaoUtils.getDaoClasses());
    return components;
  }

  /**
   * The stuff required to display the db upgrade form in webapp.
   * Needs to be connected to db.
   */
  Collection level2Components() {
    return Lists.newArrayList(
      DefaultServerUpgradeStatus.class,
      DatabaseMigrator.class,

      // plugins
      ServerPluginJarsInstaller.class,
      ServerPluginJarInstaller.class,
      InstalledPluginReferentialFactory.class,
      ServerPluginRepository.class,
      ServerExtensionInstaller.class,

      // depends on plugins
      RailsAppsDeployer.class,
      JRubyI18n.class,
      DefaultI18n.class,
      RuleI18nManager.class,
      GwtI18n.class,
      Durations.class,

      // ws
      RestartHandler.class,
      SystemWs.class
      );
  }

  /**
   * The core components that complete the initialization of database
   * when its schema is up-to-date.
   */
  Collection level3Components() {
    return Lists.newArrayList(
      PersistentSettings.class,
      DefaultDatabaseConnector.class,
      ThreadLocalDatabaseSessionFactory.class,
      new DatabaseSessionProvider(),
      ServerMetadataPersister.class,
      HttpDownloader.class,
      UriReader.class,
      ServerIdGenerator.class
      );
  }

  void startLevel4Components(ComponentContainer pico) {
    pico.addSingleton(PluginDownloader.class);
    pico.addSingleton(ChartFactory.class);
    pico.addSingleton(Languages.class);
    pico.addSingleton(Views.class);
    pico.addSingleton(CodeColorizers.class);
    pico.addSingleton(ResourceTypes.class);
    pico.addSingleton(SettingsChangeNotifier.class);
    pico.addSingleton(PageDecorations.class);
    pico.addSingleton(PreviewCache.class);
    pico.addSingleton(DefaultResourcePermissions.class);
    pico.addSingleton(Periods.class);
    pico.addSingleton(ServerWs.class);
    pico.addSingleton(BackendCleanup.class);

    // batch
    pico.addSingleton(BatchIndex.class);
    pico.addSingleton(GlobalReferentialsAction.class);
    pico.addSingleton(ProjectReferentialsAction.class);
    pico.addSingleton(UploadReportAction.class);
    pico.addSingleton(BatchWs.class);

    // update center
    pico.addSingleton(UpdateCenterClient.class);
    pico.addSingleton(UpdateCenterMatrixFactory.class);
    pico.addSingleton(UpdateCenterWs.class);

    // quality profile
    pico.addSingleton(XMLProfileParser.class);
    pico.addSingleton(XMLProfileSerializer.class);
    pico.addSingleton(AnnotationProfileParser.class);
    pico.addSingleton(QProfiles.class);
    pico.addSingleton(QProfileLookup.class);
    pico.addSingleton(QProfileProjectOperations.class);
    pico.addSingleton(QProfileProjectLookup.class);
    pico.addSingleton(QProfileRepositoryExporter.class);
    pico.addSingleton(BuiltInProfiles.class);
    pico.addSingleton(QProfileRestoreBuiltInAction.class);
    pico.addSingleton(QProfilesWs.class);
    pico.addSingleton(ProfilesWs.class);
    pico.addSingleton(RuleActivationActions.class);
    pico.addSingleton(BulkRuleActivationActions.class);
    pico.addSingleton(RuleActivator.class);
    pico.addSingleton(QProfileLoader.class);
    pico.addSingleton(QProfileExporters.class);
    pico.addSingleton(QProfileService.class);
    pico.addSingleton(RuleActivatorContextFactory.class);
    pico.addSingleton(QProfileFactory.class);
    pico.addSingleton(QProfileCopier.class);
    pico.addSingleton(QProfileBackuper.class);
    pico.addSingleton(QProfileReset.class);
    pico.addSingleton(RubyQProfileActivityService.class);

    // rule
    pico.addSingleton(AnnotationRuleParser.class);
    pico.addSingleton(XMLRuleParser.class);
    pico.addSingleton(DefaultRuleFinder.class);
    pico.addSingleton(RuleOperations.class);
    pico.addSingleton(RubyRuleService.class);
    pico.addSingleton(RuleRepositories.class);
    pico.addSingleton(DeprecatedRulesDefinition.class);
    pico.addSingleton(RuleDefinitionsLoader.class);
    pico.addSingleton(RulesDefinitionXmlLoader.class);
    pico.addSingleton(RuleService.class);
    pico.addSingleton(RuleUpdater.class);
    pico.addSingleton(RuleCreator.class);
    pico.addSingleton(RuleDeleter.class);
    pico.addSingleton(UpdateAction.class);
    pico.addSingleton(RulesWebService.class);
    pico.addSingleton(SearchAction.class);
    pico.addSingleton(org.sonar.server.rule.ws.ShowAction.class);
    pico.addSingleton(org.sonar.server.rule.ws.CreateAction.class);
    pico.addSingleton(DeleteAction.class);
    pico.addSingleton(TagsAction.class);
    pico.addSingleton(RuleMapping.class);
    pico.addSingleton(ActiveRuleCompleter.class);
    pico.addSingleton(AppAction.class);

    // activity
    pico.addSingleton(ActivitiesWebService.class);
    pico.addSingleton(org.sonar.server.activity.ws.SearchAction.class);
    pico.addSingleton(ActivityMapping.class);

    // measure
    pico.addComponent(MeasuresDao.class, false);
    pico.addSingleton(MeasureFilterFactory.class);
    pico.addSingleton(MeasureFilterExecutor.class);
    pico.addSingleton(MeasureFilterEngine.class);
    pico.addSingleton(DefaultMetricFinder.class);
    pico.addSingleton(ServerLifecycleNotifier.class);
    pico.addSingleton(TimeMachineWs.class);
    pico.addSingleton(ManualMeasuresWs.class);
    pico.addSingleton(MetricsWs.class);

    // quality gates
    pico.addSingleton(QualityGateDao.class);
    pico.addSingleton(QualityGateConditionDao.class);
    pico.addSingleton(QualityGates.class);
    pico.addSingleton(ProjectQgateAssociationDao.class);
    pico.addSingleton(QgateProjectFinder.class);

    pico.addSingleton(QGatesListAction.class);
    pico.addSingleton(QGatesSearchAction.class);
    pico.addSingleton(QGatesShowAction.class);
    pico.addSingleton(QGatesCreateAction.class);
    pico.addSingleton(QGatesRenameAction.class);
    pico.addSingleton(QGatesCopyAction.class);
    pico.addSingleton(QGatesDestroyAction.class);
    pico.addSingleton(QGatesSetAsDefaultAction.class);
    pico.addSingleton(QGatesUnsetDefaultAction.class);
    pico.addSingleton(QGatesSelectAction.class);
    pico.addSingleton(QGatesDeselectAction.class);
    pico.addSingleton(QGatesCreateConditionAction.class);
    pico.addSingleton(QGatesDeleteConditionAction.class);
    pico.addSingleton(QGatesUpdateConditionAction.class);
    pico.addSingleton(QGatesAppAction.class);
    pico.addSingleton(QGatesWs.class);

    // web services
    pico.addSingleton(WebServiceEngine.class);
    pico.addSingleton(ListingWs.class);

    // localization
    pico.addSingleton(L10nWs.class);

    // authentication
    pico.addSingleton(AuthenticationWs.class);

    // users
    pico.addSingleton(SecurityRealmFactory.class);
    pico.addSingleton(HibernateUserFinder.class);
    pico.addSingleton(NewUserNotifier.class);
    pico.addSingleton(DefaultUserFinder.class);
    pico.addSingleton(DefaultUserService.class);
    pico.addSingleton(UsersWs.class);
    pico.addSingleton(FavoritesWs.class);
    pico.addSingleton(UserPropertiesWs.class);

    // groups
    pico.addSingleton(GroupMembershipService.class);
    pico.addSingleton(GroupMembershipFinder.class);

    // permissions
    pico.addSingleton(PermissionFacade.class);
    pico.addSingleton(InternalPermissionService.class);
    pico.addSingleton(InternalPermissionTemplateService.class);
    pico.addSingleton(PermissionFinder.class);
    pico.addSingleton(PermissionsWs.class);

    // components
    pico.addSingleton(DefaultComponentFinder.class);
    pico.addSingleton(DefaultRubyComponentService.class);
    pico.addSingleton(ComponentDao.class);
    pico.addSingleton(ResourcesWs.class);
    pico.addSingleton(ComponentsWs.class);
    pico.addSingleton(ProjectsWs.class);
    pico.addSingleton(ComponentAppAction.class);
    pico.addSingleton(EventsWs.class);
    pico.addSingleton(ComponentCleanerService.class);

    // issues
    pico.addSingleton(ServerIssueStorage.class);
    pico.addSingleton(IssueUpdater.class);
    pico.addSingleton(FunctionExecutor.class);
    pico.addSingleton(IssueWorkflow.class);
    pico.addSingleton(IssueCommentService.class);
    pico.addSingleton(DefaultIssueFinder.class);
    pico.addSingleton(IssueStatsFinder.class);
    pico.addSingleton(PublicRubyIssueService.class);
    pico.addSingleton(InternalRubyIssueService.class);
    pico.addSingleton(IssueChangelogService.class);
    pico.addSingleton(IssueNotifications.class);
    pico.addSingleton(ActionService.class);
    pico.addSingleton(Actions.class);
    pico.addSingleton(IssueBulkChangeService.class);
    pico.addSingleton(IssueChangelogFormatter.class);
    pico.addSingleton(IssuesWs.class);
    pico.addSingleton(IssueShowAction.class);
    pico.addSingleton(org.sonar.server.issue.ws.SearchAction.class);
    pico.addSingleton(IssueService.class);
    pico.addSingleton(IssueActionsWriter.class);

    // issue filters
    pico.addSingleton(IssueFilterService.class);
    pico.addSingleton(IssueFilterSerializer.class);
    pico.addSingleton(IssueFilterWs.class);
    pico.addSingleton(IssueFilterWriter.class);
    pico.addSingleton(org.sonar.server.issue.filter.AppAction.class);
    pico.addSingleton(org.sonar.server.issue.filter.ShowAction.class);
    pico.addSingleton(org.sonar.server.issue.filter.FavoritesAction.class);

    // action plan
    pico.addSingleton(ActionPlanWs.class);
    pico.addSingleton(ActionPlanService.class);

    // issues actions
    pico.addSingleton(AssignAction.class);
    pico.addSingleton(PlanAction.class);
    pico.addSingleton(SetSeverityAction.class);
    pico.addSingleton(CommentAction.class);
    pico.addSingleton(TransitionAction.class);

    // technical debt
    pico.addSingleton(DebtModelService.class);
    pico.addSingleton(DebtModelOperations.class);
    pico.addSingleton(DebtModelLookup.class);
    pico.addSingleton(DebtModelBackup.class);
    pico.addSingleton(DebtModelPluginRepository.class);
    pico.addSingleton(DebtModelXMLExporter.class);
    pico.addSingleton(DebtRulesXMLImporter.class);
    pico.addSingleton(DebtCharacteristicsXMLImporter.class);

    // source
    pico.addSingleton(HtmlSourceDecorator.class);
    pico.addSingleton(DeprecatedSourceDecorator.class);
    pico.addSingleton(SourceService.class);
    pico.addSingleton(SourcesWs.class);
    pico.addSingleton(ShowAction.class);
    pico.addSingleton(ScmWriter.class);
    pico.addSingleton(ScmAction.class);

    // Duplications
    pico.addSingleton(DuplicationsParser.class);
    pico.addSingleton(DuplicationsWs.class);
    pico.addSingleton(DuplicationsJsonWriter.class);
    pico.addSingleton(org.sonar.server.duplication.ws.ShowAction.class);

    // text
    pico.addSingleton(MacroInterpreter.class);
    pico.addSingleton(RubyTextService.class);

    // Notifications
    pico.addSingleton(EmailSettings.class);
    pico.addSingleton(NotificationService.class);
    pico.addSingleton(NotificationCenter.class);
    pico.addSingleton(DefaultNotificationManager.class);

    // Tests
    pico.addSingleton(CoverageService.class);
    pico.addSingleton(CoverageWs.class);
    pico.addSingleton(CoverageShowAction.class);
    pico.addSingleton(TestsWs.class);
    pico.addSingleton(TestsTestCasesAction.class);
    pico.addSingleton(TestsCoveredFilesAction.class);
    pico.addSingleton(TestsShowAction.class);

    // Properties
    pico.addSingleton(PropertiesWs.class);

    // graphs and perspective related classes
    pico.addSingleton(TestablePerspectiveLoader.class);
    pico.addSingleton(TestPlanPerspectiveLoader.class);
    pico.addSingleton(SnapshotPerspectives.class);

    // Type validation
    pico.addSingleton(TypeValidations.class);
    pico.addSingleton(IntegerTypeValidation.class);
    pico.addSingleton(FloatTypeValidation.class);
    pico.addSingleton(BooleanTypeValidation.class);
    pico.addSingleton(TextTypeValidation.class);
    pico.addSingleton(StringTypeValidation.class);
    pico.addSingleton(StringListTypeValidation.class);

    // Design
    pico.addSingleton(FileDesignWidget.class);
    pico.addSingleton(PackageDesignWidget.class);

    // Compute engine
    pico.addSingleton(ComputationService.class);

    for (Object components : level4AddedComponents) {
      pico.addSingleton(components);
    }

    ServerExtensionInstaller extensionInstaller = pico.getComponentByType(ServerExtensionInstaller.class);
    extensionInstaller.installExtensions(pico);

    pico.startComponents();
  }

  void addComponents(Collection components) {
    this.level4AddedComponents.addAll(components);
  }

  public void executeStartupTasks(ComponentContainer pico) {

    final ComponentContainer startupContainer = pico.createChild();

    /** Index startup Synchronization */
    startupContainer.addSingleton(IndexSynchronizer.class);

    startupContainer.addSingleton(GwtPublisher.class);
    startupContainer.addSingleton(RegisterMetrics.class);
    startupContainer.addSingleton(RegisterQualityGates.class);
    startupContainer.addSingleton(RegisterRules.class);
    startupContainer.addSingleton(RegisterQualityProfiles.class);
    startupContainer.addSingleton(JdbcDriverDeployer.class);
    startupContainer.addSingleton(RegisterDebtModel.class);
    startupContainer.addSingleton(GeneratePluginIndex.class);
    startupContainer.addSingleton(RegisterNewMeasureFilters.class);
    startupContainer.addSingleton(RegisterDashboards.class);
    startupContainer.addSingleton(RegisterPermissionTemplates.class);
    startupContainer.addSingleton(RenameDeprecatedPropertyKeys.class);
    startupContainer.addSingleton(LogServerId.class);
    startupContainer.addSingleton(RegisterServletFilters.class);
    startupContainer.addSingleton(CleanPreviewAnalysisCache.class);
    startupContainer.addSingleton(CopyRequirementsFromCharacteristicsToRules.class);
    startupContainer.addSingleton(AnalysisReportTasksCleaner.class);

    DoPrivileged.execute(new DoPrivileged.Task() {
      @Override
      protected void doPrivileged() {
        startupContainer.getComponentsByType(IndexSynchronizer.class).get(0).execute();
        startupContainer.startComponents();
        startupContainer.getComponentByType(ServerLifecycleNotifier.class).notifyStart();
      }
    });

    // Do not put the following statements in a finally block.
    // It would hide the possible exception raised during startup
    // See SONAR-3107
    startupContainer.stopComponents();

    pico.getComponentByType(DatabaseSessionFactory.class).clear();
  }
}
