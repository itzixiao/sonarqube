/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonar.server.user.ws;

import java.util.List;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.property.PropertyDto;
import org.sonar.db.property.PropertyQuery;
import org.sonar.server.user.UserSession;

import static com.google.common.base.Preconditions.checkState;

public class DismissNoticeAction implements UsersWsAction {

  private static final String EDUCATION_PRINCIPLES = "educationPrinciples";
  private static final String SONARLINT_AD = "sonarlintAd";
  private static final String ISSUE_CLEAN_CODE_GUIDE = "issueCleanCodeGuide";
  private static final String QUALITY_GATE_CAYC_CONDITIONS_SIMPLIFICATION = "qualityGateCaYCConditionsSimplification";

  protected static final List<String> AVAILABLE_NOTICE_KEYS = List.of(EDUCATION_PRINCIPLES, SONARLINT_AD, ISSUE_CLEAN_CODE_GUIDE, QUALITY_GATE_CAYC_CONDITIONS_SIMPLIFICATION);
  public static final String USER_DISMISS_CONSTANT = "user.dismissedNotices.";

  private final UserSession userSession;
  private final DbClient dbClient;

  public DismissNoticeAction(UserSession userSession, DbClient dbClient) {
    this.userSession = userSession;
    this.dbClient = dbClient;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("dismiss_notice")
      .setDescription("Dismiss a notice for the current user. Silently ignore if the notice is already dismissed.")
      .setChangelog(new Change("10.2", "Support for new notice '%s' was added.".formatted(ISSUE_CLEAN_CODE_GUIDE)))
      .setChangelog(new Change("10.3", "Support for new notice '%s' was added.".formatted(QUALITY_GATE_CAYC_CONDITIONS_SIMPLIFICATION)))
      .setSince("9.6")
      .setInternal(true)
      .setHandler(this)
      .setPost(true);

    action.createParam("notice")
      .setDescription("notice key to dismiss")
      .setExampleValue(EDUCATION_PRINCIPLES)
      .setPossibleValues(AVAILABLE_NOTICE_KEYS);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn();
    String currentUserUuid = userSession.getUuid();
    checkState(currentUserUuid != null, "User uuid should not be null");

    String noticeKeyParam = request.mandatoryParam("notice");

    dismissNotice(response, currentUserUuid, noticeKeyParam);
  }

  public void dismissNotice(Response response, String currentUserUuid, String noticeKeyParam) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      String paramKey = USER_DISMISS_CONSTANT + noticeKeyParam;
      PropertyQuery query = new PropertyQuery.Builder()
        .setUserUuid(currentUserUuid)
        .setKey(paramKey)
        .build();

      if (!dbClient.propertiesDao().selectByQuery(query, dbSession).isEmpty()) {
        // already dismissed
        response.noContent();
      }

      PropertyDto property = new PropertyDto().setUserUuid(currentUserUuid).setKey(paramKey);
      dbClient.propertiesDao().saveProperty(dbSession, property);
      dbSession.commit();
      response.noContent();
    }
  }
}
