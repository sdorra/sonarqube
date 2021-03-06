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
package org.sonar.server.search;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequestBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerComponent;
import org.sonar.api.platform.ComponentContainer;
import org.sonar.core.cluster.WorkQueue;
import org.sonar.server.search.action.IndexAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class IndexQueue implements ServerComponent, WorkQueue<IndexAction<?>> {

  private final SearchClient searchClient;
  private final ComponentContainer container;

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexQueue.class);

  private static final Integer CONCURRENT_NORMALIZATION_FACTOR = 1;

  public IndexQueue(SearchClient searchClient, ComponentContainer container) {
    this.searchClient = searchClient;
    this.container = container;
  }

  @Override
  public void enqueue(List<IndexAction<?>> actions) {
    if (actions.isEmpty()) {
      return;
    }
    try {

      boolean refreshRequired = false;

      Map<String, Index> indexes = getIndexMap();
      Set<String> indices = new HashSet<String>();
      for (IndexAction action : actions) {
        Index index = indexes.get(action.getIndexType());
        action.setIndex(index);
        if (action.needsRefresh()) {
          refreshRequired = true;
          indices.add(index.getIndexName());
        }
      }

      BulkRequestBuilder bulkRequestBuilder = new BulkRequestBuilder(searchClient);

      long normTime = processActionsIntoQueries(bulkRequestBuilder, actions);

      if (bulkRequestBuilder.numberOfActions() > 0) {
        // execute the request
        long indexTime = System.currentTimeMillis();
        BulkResponse response = searchClient.execute(bulkRequestBuilder.setRefresh(false));

        indexTime = System.currentTimeMillis() - indexTime;

        long refreshTime = 0;
        if (refreshRequired) {
          refreshTime = this.refreshRequiredIndex(indices);
        }

        LOGGER.debug("-- submitted {} items with {}ms in normalization, {}ms indexing and {}ms refresh({}). Total: {}ms",
          bulkRequestBuilder.numberOfActions(), normTime, indexTime, refreshTime, indices, (normTime + indexTime + refreshTime));

        if (response.hasFailures()) {
          throw new IllegalStateException("Errors while indexing stack: " + response.buildFailureMessage());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Could not commit to ElasticSearch", e);
    }
  }

  private long refreshRequiredIndex(Set<String> indices) {

    long refreshTime = System.currentTimeMillis();
    if (!indices.isEmpty()) {
      RefreshRequestBuilder refreshRequest = searchClient.admin().indices()
        .prepareRefresh(indices.toArray(new String[indices.size()]))
        .setForce(false);

      RefreshResponse refreshResponse = searchClient.execute(refreshRequest);

      if (refreshResponse.getFailedShards() > 0) {
        LOGGER.warn("{} Shard(s) did not refresh", refreshResponse.getFailedShards());
      }
    }
    return System.currentTimeMillis() - refreshTime;
  }

  private long processActionsIntoQueries(BulkRequestBuilder bulkRequestBuilder, List<IndexAction<?>> actions) {
    long normTime = System.currentTimeMillis();
    try {
      boolean hasInlineRefreshRequest = false;
      ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_NORMALIZATION_FACTOR);
      // invokeAll() blocks until ALL tasks submitted to executor complete
      List<Future<List<? extends ActionRequest>>> requests = executorService.invokeAll(actions, 20, TimeUnit.MINUTES);
      for (Future<List<? extends ActionRequest>> updates : requests) {
        for (ActionRequest update : updates.get()) {

          if (IndexRequest.class.isAssignableFrom(update.getClass())) {
            bulkRequestBuilder.add((IndexRequest) update);
          } else if (UpdateRequest.class.isAssignableFrom(update.getClass())) {
            bulkRequestBuilder.add((UpdateRequest) update);
          } else if (DeleteRequest.class.isAssignableFrom(update.getClass())) {
            bulkRequestBuilder.add((DeleteRequest) update);
          } else if (RefreshRequest.class.isAssignableFrom(update.getClass())) {
            hasInlineRefreshRequest = true;
          } else {
            throw new IllegalStateException("Un-managed request type: " + update.getClass());
          }
        }
      }
      executorService.shutdown();
      bulkRequestBuilder.setRefresh(hasInlineRefreshRequest);
    } catch (Exception e) {
      throw new IllegalStateException("Could not execute normalization for stack", e);
    }
    return System.currentTimeMillis() - normTime;
  }

  private Map<String, Index> getIndexMap() {
    Map<String, Index> indexes = new HashMap<String, Index>();
    for (Index index : container.getComponentsByType(Index.class)) {
      indexes.put(index.getIndexType(), index);
    }
    return indexes;
  }
}
