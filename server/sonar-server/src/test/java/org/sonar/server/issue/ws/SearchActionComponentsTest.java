/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import java.util.Date;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.resources.Languages;
import org.sonar.api.utils.Durations;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.IssueQueryFactory;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;
import org.sonar.server.permission.index.AuthorizationTypeSupport;
import org.sonar.server.permission.index.PermissionIndexerTester;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonar.server.ws.WsResponseCommonFormat;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.Issues.SearchWsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.utils.DateUtils.addDays;
import static org.sonar.db.component.ComponentTesting.newProjectCopy;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_COMPONENT_KEYS;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_SINCE_LEAK_PERIOD;

public class SearchActionComponentsTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create();
  @Rule
  public EsTester es = new EsTester(new IssueIndexDefinition(new MapSettings().asConfig()));

  private IssueIndex index = new IssueIndex(es.client(), System2.INSTANCE, userSession, new AuthorizationTypeSupport(userSession));
  private IssueIndexer indexer = new IssueIndexer(es.client(), db.getDbClient(), new IssueIteratorFactory(db.getDbClient()));
  private PermissionIndexerTester permissionIndexer = new PermissionIndexerTester(es, indexer);
  private IssueQueryFactory issueQueryFactory = new IssueQueryFactory(db.getDbClient(), System2.INSTANCE, userSession);
  private SearchResponseLoader searchResponseLoader = new SearchResponseLoader(userSession, db.getDbClient(), null, null);
  private SearchResponseFormat searchResponseFormat = new SearchResponseFormat(new Durations(), new WsResponseCommonFormat(new Languages()), new Languages(),
    new AvatarResolverImpl());

  private WsActionTester ws = new WsActionTester(new SearchAction(userSession, index, issueQueryFactory, searchResponseLoader, searchResponseFormat));

  @Test
  public void search_by_application_key() throws Exception {
    ComponentDto project1 = db.components().insertPublicProject();
    ComponentDto project2 = db.components().insertPublicProject();
    ComponentDto application = db.components().insertApplication(db.getDefaultOrganization());
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    IssueDto issue1 = db.issues().insertIssue(i -> i.setProject(project1));
    IssueDto issue2 = db.issues().insertIssue(i -> i.setProject(project2));
    userSession.registerComponents(application, project1, project2);
    permissionIndexer.allowOnlyAnyone(project1);
    permissionIndexer.allowOnlyAnyone(project2);
    indexer.indexOnStartup(null);

    SearchWsResponse result = ws.newRequest()
      .setParam(PARAM_COMPONENT_KEYS, application.getKey())
      .executeProtobuf(SearchWsResponse.class);

    assertThat(result.getIssuesList()).extracting(Issue::getKey)
      .containsExactlyInAnyOrder(issue1.getKey(), issue2.getKey());
  }

  @Test
  public void ignore_application_without_browse_permission() {
    ComponentDto project = db.components().insertPublicProject();
    ComponentDto application = db.components().insertApplication(db.getDefaultOrganization());
    db.components().insertComponents(newProjectCopy("PC1", project, application));
    db.issues().insertIssue(i -> i.setProject(project));
    userSession.registerComponents(project);
    permissionIndexer.allowOnlyAnyone(project);
    indexer.indexOnStartup(null);

    SearchWsResponse result = ws.newRequest()
      .setParam(PARAM_COMPONENT_KEYS, application.getKey())
      .executeProtobuf(SearchWsResponse.class);

    assertThat(result.getIssuesList()).isEmpty();
  }

  @Test
  public void search_application_without_projects() {
    ComponentDto project = db.components().insertPublicProject();
    ComponentDto application = db.components().insertApplication(db.getDefaultOrganization());
    db.issues().insertIssue(i -> i.setProject(project));
    userSession.registerComponents(application, project);
    permissionIndexer.allowOnlyAnyone(project);
    indexer.indexOnStartup(null);

    SearchWsResponse result = ws.newRequest()
      .setParam(PARAM_COMPONENT_KEYS, application.getKey())
      .executeProtobuf(SearchWsResponse.class);

    assertThat(result.getIssuesList()).isEmpty();
  }

  @Test
  public void search_by_application_and_by_leak() throws Exception {
    Date now = new Date();
    ComponentDto application = db.components().insertApplication(db.getDefaultOrganization());
    // Project 1
    ComponentDto project1 = db.components().insertPublicProject();
    db.components().insertSnapshot(project1, s -> s.setPeriodDate(addDays(now, -14).getTime()));
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    IssueDto project1Issue1 = db.issues().insertIssue(i -> i.setProject(project1).setIssueCreationDate(addDays(now, -10)));
    IssueDto project1Issue2 = db.issues().insertIssue(i -> i.setProject(project1).setIssueCreationDate(addDays(now, -20)));
    // Project 2
    ComponentDto project2 = db.components().insertPublicProject();
    db.components().insertSnapshot(project2, s -> s.setPeriodDate(addDays(now, -25).getTime()));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    IssueDto project2Issue1 = db.issues().insertIssue(i -> i.setProject(project2).setIssueCreationDate(addDays(now, -15)));
    IssueDto project2Issue2 = db.issues().insertIssue(i -> i.setProject(project2).setIssueCreationDate(addDays(now, -30)));
    // Permissions and index
    userSession.registerComponents(application, project1, project2);
    permissionIndexer.allowOnlyAnyone(project1);
    permissionIndexer.allowOnlyAnyone(project2);
    indexer.indexOnStartup(null);

    SearchWsResponse result = ws.newRequest()
      .setParam(PARAM_COMPONENT_KEYS, application.getKey())
      .setParam(PARAM_SINCE_LEAK_PERIOD, "true")
      .executeProtobuf(SearchWsResponse.class);

    assertThat(result.getIssuesList()).extracting(Issue::getKey)
      .containsExactlyInAnyOrder(project1Issue1.getKey(), project2Issue1.getKey())
      .doesNotContain(project1Issue2.getKey(), project2Issue2.getKey());
  }

  @Test
  public void search_by_application_and_by_leak_when_one_project_has_no_leak() throws Exception {
    Date now = new Date();
    ComponentDto application = db.components().insertApplication(db.getDefaultOrganization());
    // Project 1
    ComponentDto project1 = db.components().insertPublicProject();
    db.components().insertSnapshot(project1, s -> s.setPeriodDate(addDays(now, -14).getTime()));
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    IssueDto project1Issue1 = db.issues().insertIssue(i -> i.setProject(project1).setIssueCreationDate(addDays(now, -10)));
    IssueDto project1Issue2 = db.issues().insertIssue(i -> i.setProject(project1).setIssueCreationDate(addDays(now, -20)));
    // Project 2, without leak => no issue form it should be returned
    ComponentDto project2 = db.components().insertPublicProject();
    db.components().insertSnapshot(project2, s -> s.setPeriodDate(null));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    IssueDto project2Issue1 = db.issues().insertIssue(i -> i.setProject(project2).setIssueCreationDate(addDays(now, -15)));
    IssueDto project2Issue2 = db.issues().insertIssue(i -> i.setProject(project2).setIssueCreationDate(addDays(now, -30)));
    // Permissions and index
    userSession.registerComponents(application, project1, project2);
    permissionIndexer.allowOnlyAnyone(project1);
    permissionIndexer.allowOnlyAnyone(project2);
    indexer.indexOnStartup(null);

    SearchWsResponse result = ws.newRequest()
      .setParam(PARAM_COMPONENT_KEYS, application.getKey())
      .setParam(PARAM_SINCE_LEAK_PERIOD, "true")
      .executeProtobuf(SearchWsResponse.class);

    assertThat(result.getIssuesList()).extracting(Issue::getKey)
      .containsExactlyInAnyOrder(project1Issue1.getKey())
      .doesNotContain(project1Issue2.getKey(), project2Issue1.getKey(), project2Issue2.getKey());
  }
}
