/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.component.tests.doctor.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Configuration for Retrieval Augmented Generation (RAG). This class configures how the AI agent
 * retrieves relevant context from the knowledge base.
 */
@ApplicationScoped
public class RagConfiguration {

  /**
   * Creates a RetrievalAugmentor that enables RAG for the AI agent. This allows the agent to search
   * PGVector for relevant code/documentation before answering questions.
   *
   * @param embeddingModel the model used to generate embeddings for queries
   * @param embeddingStore the PGVector store containing ingested documents
   * @return configured RetrievalAugmentor
   */
  @Produces
  @ApplicationScoped
  public RetrievalAugmentor createRetrievalAugmentor(
      EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {

    Log.debug("RAG Configuration: Creating RetrievalAugmentor with PGVector");
    Log.debug("   - Max results: 20");
    Log.debug("   - Min similarity score: 0.5");

    // Create a content retriever that searches PGVector
    ContentRetriever contentRetriever =
        EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(20) // Retrieve top 20 most relevant documents for better context
            .minScore(0.5) // Lower threshold to catch more potential matches
            .build();

    // Build the retrieval augmentor
    RetrievalAugmentor augmentor =
        DefaultRetrievalAugmentor.builder().contentRetriever(contentRetriever).build();

    Log.debug("RAG is now active - agent will retrieve context from knowledge base");
    return augmentor;
  }
}
