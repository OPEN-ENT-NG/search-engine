/*
 * Copyright © Conseil Régional Nord Pas de Calais - Picardie, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.searchengine.controllers;

import fr.openent.searchengine.SearchEngine;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.shareddata.LocalMap;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.search.SearchingHandler;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import java.util.*;

/**
 * Vert.x backend controller.
 */
public class SearchEngineController extends BaseController {
	private EventStore eventStore;
	private enum SearchEngineEvent { ACCESS }
	private Integer maxSecTimeAllowed;
	private Integer pagingSizePerCollection;
	private Integer searchWordMinSize;
	private static final I18n i18n = I18n.getInstance();
	private static final String[] RESULT_COLUMNS_HEADER = new String[] {"title", "description", "modified", "ownerDisplayName", "ownerId", "url"};

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		eventStore = EventStoreFactory.getFactory().getEventStore(SearchEngine.class.getSimpleName());
	}

	/**
	 * Creates a new controller.
	 */
	public SearchEngineController(final Integer maxSecTimeAllowed, final Integer pagingSizePerCollection,
								  final Integer searchWordMinSize) {
		this.maxSecTimeAllowed = maxSecTimeAllowed;
		this.pagingSizePerCollection = pagingSizePerCollection;
		this.searchWordMinSize = searchWordMinSize;
	}

	@Get("")
	@ApiDoc("Allows to display the main view")
	@SecuredAction("searchengine.auth")
	public void view(HttpServerRequest request) {
		renderView(request);

		/// Create event "access to application Search Engine" and store it, for module "statistics"
		eventStore.createAndStoreEvent(SearchEngineEvent.ACCESS.name(), request);
	}

	@Get("/types")
	@SecuredAction(value = "searchengine.auth", type = ActionType.AUTHENTICATED)
	public void listTypes(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			public void handle(final UserInfos user) {
				if (user != null) {
					final JsonArray types = new JsonArray();
					final LocalMap<String, String> appRegistered = vertx.sharedData()
							.getLocalMap(SearchingHandler.class.getName());
					for (final String app : appRegistered.keySet()) {
						types.add(app);
					}
					renderJson(request, types);
				} else {
					if (log.isDebugEnabled()) {
						log.debug("User not found in session.");
					}
					Renders.unauthorized(request);
				}
			}
		});
	}

	/**
	 * search.
	 * @param request Client request.
	 */
	@Post("")
	@SecuredAction(value = "searchengine.view", type = ActionType.AUTHENTICATED)
	public void search(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "search", new Handler<JsonObject>() {
						public void handle(JsonObject data) {
							final JsonArray searchWords = checkAndComposeWordFromSearchText(data.getString("searchText", ""));
							final Integer currentPage = data.getInteger("currentPage", 0);
							final JsonArray types = data.getJsonArray("filter", new JsonArray());
							final String locale = I18n.acceptLanguage(request);

							if (searchWords.size() != 0 && types.size() != 0) {
								final String searchId = UUID.randomUUID().toString();
								final Boolean[] treatyIsSoLong = new Boolean[]{Boolean.FALSE};
								//Check is a completed result
								final Set<Boolean> isCompletedResult = new HashSet<Boolean>();
								//Pending vert.x 3 with TimeoutHandler
								final long timerID = vertx.setTimer(SearchEngineController.this.maxSecTimeAllowed * 1000, new Handler<Long>() {
									@Override
									public void handle(Long aLong) {
										treatyIsSoLong[0] = true;
									}
								});

								final LocalMap<String, String> appRegistered = vertx.sharedData()
										.getLocalMap(SearchingHandler.class.getName());
								final Set<String> appRegisteredUntreated = new HashSet<>(appRegistered.keySet());
								final JsonArray results = new JsonArray();
								final String address = "search." + searchId;

								final MessageConsumer<JsonObject> messageConsumer = eb.localConsumer(address);

								final Handler<Message<JsonObject>> searchHandler = new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> event) {
										final String app = event.body().getString("application");
										appRegisteredUntreated.remove(app);

										if (log.isDebugEnabled()) {
											log.debug("Search engine " + searchId + ", handle a result for : " +
													app);
										}

										final String replyMessage = checkCurrentResult(event.body().getValue("results"));
										event.reply(new JsonObject().put("message", replyMessage));

										final JsonArray responseResults = event.body().getJsonArray("results");

										if ("ok".equals(replyMessage) && responseResults.size() > 0) {
											//check for feedback on next result
											final int realSizeResult;
											if (responseResults.size() > SearchEngineController.this.pagingSizePerCollection) {
												isCompletedResult.add(false);
												//delete the marker
												realSizeResult = responseResults.size() -1;
											} else {
												realSizeResult = responseResults.size();
											}
											for (int i=0;i<realSizeResult;i++) {
												final JsonObject jo = responseResults.getJsonObject(i);
												//add the origin of the result
												jo.put("app", app);
												results.add(jo);
											}
										}

										if (appRegisteredUntreated.isEmpty() || treatyIsSoLong[0]) {
											final Boolean hasPartialResult;
											if (treatyIsSoLong[0] && !appRegisteredUntreated.isEmpty()) {
												hasPartialResult = true;
												log.warn("search engine performed a partial search for the term configuration was exceeded");
											} else {
												vertx.cancelTimer(timerID);
												hasPartialResult = false;
											}

											if (results.size() == 0 && currentPage.equals(0)) {
												//fixme can't use 404 because reverse proxy converts this error to html
												badRequest(request, "search.engine.empty");
											} else {
												renderJson(request, new JsonObject().put("status", hasPartialResult)
														.put("hasMoreResult", isCompletedResult.contains(false)).put("results", results));
											}
											messageConsumer.unregister();
											if (log.isDebugEnabled()) {
												log.debug("Search engine unregister handle : " + searchId);
											}
										}
									}
								};

								messageConsumer.handler(searchHandler);

								publish(user, searchId, currentPage, searchWords, types, locale);
							} else {
								Renders.badRequest(request, i18n.translate("search.engine.bad.search.criteria", Renders.getHost(request), I18n.acceptLanguage(request),
										SearchEngineController.this.searchWordMinSize.toString()));
							}
						}
					});
				} else {
					if (log.isDebugEnabled()) {
						log.debug("User not found in session.");
					}
					Renders.unauthorized(request);
				}
			}
		});
	}

	private String checkCurrentResult(final Object results) {
		final String message;
		if (!(results instanceof JsonArray)) {
			message = "the result must be a JsonArray";
		} else {
			final JsonArray jArray = (JsonArray) results;

			if (jArray.size() != 0) {
				final Object obj = jArray.getValue(0);
				if (!(obj instanceof JsonObject)) {
					message = "Contain of JsonArray must be JsonObject";
				} else {
					final JsonObject jObj = (JsonObject) obj;
					if (jObj.size() != RESULT_COLUMNS_HEADER.length ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[0]) ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[1]) ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[2]) ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[3]) ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[4]) ||
							!jObj.containsKey(RESULT_COLUMNS_HEADER[5])) {
						message = "JsonObject must contain six entries : " + RESULT_COLUMNS_HEADER[0] +
								"," + RESULT_COLUMNS_HEADER[1] + "," + RESULT_COLUMNS_HEADER[2] + "," + RESULT_COLUMNS_HEADER[3]
								+ "," + RESULT_COLUMNS_HEADER[4] + "," + RESULT_COLUMNS_HEADER[5];
					} else {
						message = "ok";
					}
				}
			} else {
				message = "ok";
			}
		}
		return message;
	}

	private void publish(UserInfos user, String searchId, Integer currentPage, JsonArray searchWords,
						 JsonArray types, String locale) {
		final JsonObject message = new JsonObject().put("searchId", searchId);

		message.put("userId", user.getUserId());
		message.put("groupIds",  new JsonArray(user.getGroupsIds()));
		message.put("searchWords", searchWords);
		message.put("page", currentPage);
		//Increase the size page to obtain a feedback on next results
		message.put("limit", this.pagingSizePerCollection + 1);
		message.put("columnsHeader", new JsonArray(Arrays.asList(RESULT_COLUMNS_HEADER)));
		message.put("appFilters", types);
		message.put("locale", locale);

		eb.publish("search.searching", message);
	}

	private JsonArray checkAndComposeWordFromSearchText(final String searchText) {
		final JsonArray searchWords = new JsonArray();

		if (searchText != null) {
			//delete all useless spaces
			final String searchTextTreaty = searchText.replaceAll("\\s+", " ").trim();
			if (!searchTextTreaty.isEmpty()) {
				final List<String> words = Arrays.asList(searchTextTreaty.split(" "));
				//words search
				for (String w : words) {
					final String wTraity = w.replaceAll("(?!')\\p{Punct}", "");
					if (wTraity.length() >= this.searchWordMinSize) {
						searchWords.add(wTraity);
					}
				}
			}
		}
		return  searchWords;
	}
}