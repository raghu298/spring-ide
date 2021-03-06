/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.operation.IRunnableContext;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.wizard.content.ContentManager;
import org.springframework.ide.eclipse.boot.wizard.content.ContentManager.DownloadState;
import org.springframework.ide.eclipse.boot.wizard.content.ContentType;
import org.springframework.ide.eclipse.boot.wizard.guides.GSImportWizardModel;
import org.springsource.ide.eclipse.commons.frameworks.core.util.JobUtil;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

public class GSGWizardModelTest {

	@Test
	public void testPrefetchingGettingStartedContent() throws Exception {
		// Prefetching involves testing the download "states" for two different
		// steps that occur in series (prefetching content provider properties,
		// followed by prefetching actual content):
		// 1. Not started any prefetching
		// 2. Is downloading content provider properties
		// 3. content provider properties downloading completed
		// 4. content provider properties downloaded (final state)
		// 5. is downloading actual content
		// 6. actual content downloading completed (this tests a distinct state
		// that is used for
		// refreshing views right after a downloading session has completed)
		// 7. actual content downloaded. This is a post-downloading "final"
		// state
		final GSImportWizardModel model = getModel();
		final ContentManager contentManager = model.getContentManager();

		// Test the prefetching content provider properties tracker
		assertEquals(DownloadState.NOT_STARTED,
				contentManager.getPrefetchContentProviderPropertiesTracker().getValue());

		// Test the prefetching content tracker
		assertEquals(DownloadState.NOT_STARTED, contentManager.getPrefetchContentTracker().getValue());

		ContentType<?>[] types = contentManager.getTypes();
		assertTrue("Expected no content types registered before prefetching.", types == null || types.length == 0);

		List<?> allContent = getContent(types, contentManager);
		assertTrue("Expected no content for getting started guides before prefetching.", allContent.size() == 0);

		// States to test in order encountered by the listener
		final List<DownloadState> actualContentPropertiesDownloadState = new ArrayList<>(0);

		final List<DownloadState> actualContentDownloadStates = new ArrayList<>(0);

		// Listener tests that each of the expected states occur during content
		// provider properties downloading
		ValueListener<DownloadState> contentProviderPropertiesDownloadStateListener = new ValueListener<DownloadState>() {

			@Override
			public void gotValue(LiveExpression<DownloadState> exp, DownloadState actualDownloadState) {

				// Add states in the order they are received as the listener
				// should be invoked multiple times during the prefetching
				// operation
				if (!actualContentPropertiesDownloadState.contains(actualDownloadState)) {
					actualContentPropertiesDownloadState.add(actualDownloadState);
				}

			}
		};

		contentManager.getPrefetchContentProviderPropertiesTracker()
				.addListener(contentProviderPropertiesDownloadStateListener);

		// Listener tests that each of the expected states occur during content
		// downloading
		ValueListener<DownloadState> contentDownloadStateListener = new ValueListener<DownloadState>() {

			@Override
			public void gotValue(LiveExpression<DownloadState> exp, DownloadState actualDownloadState) {

				// Add states in the order they are received as the listener
				// should be invoked multiple times during the prefetching
				// operation
				if (!actualContentDownloadStates.contains(actualDownloadState)) {
					actualContentDownloadStates.add(actualDownloadState);
				}

			}
		};
		contentManager.getPrefetchContentTracker().addListener(contentDownloadStateListener);

		// BEGIN ACTUAL PREFETCH
		contentManager.prefetchInBackground(getRunnableContext());

		// Test that the listener gets notified when content provider properties
		// has been downloaded
		new ACondition(30 * 1000) {
			@Override
			public boolean test() throws Exception {
				if (actualContentPropertiesDownloadState.size() == 4) {

					assertEquals(
							"Expected the first state when prefetching content provider properties: "
									+ DownloadState.NOT_STARTED,
							actualContentPropertiesDownloadState.get(0), DownloadState.NOT_STARTED);

					assertEquals(
							"Expected the second state when prefetching content provider properties: "
									+ DownloadState.IS_DOWNLOADING,
							actualContentPropertiesDownloadState.get(1), DownloadState.IS_DOWNLOADING);
					assertEquals(
							"Expected the third state when prefetching content provider properties: "
									+ DownloadState.DOWNLOADING_COMPLETED,
							actualContentPropertiesDownloadState.get(2), DownloadState.DOWNLOADING_COMPLETED);
					assertEquals(
							"Expected the fourth state when prefetching content provider properties: "
									+ DownloadState.DOWNLOADED,
							actualContentPropertiesDownloadState.get(3), DownloadState.DOWNLOADED);

					// Final state must be "post downloaded" states
					assertEquals(
							"Expected final download state for content provider properties to be: "
									+ DownloadState.DOWNLOADED,
							DownloadState.DOWNLOADED,
							contentManager.getPrefetchContentProviderPropertiesTracker().getValue());

					ContentType<?>[] types = contentManager.getTypes();
					assertTrue("Expected content types for getting started guides after downloading completed.",
							types != null && types.length > 0);

					return true;
				} else {
					return false;
				}
			}
		};

		// Test that the listener gets notified when content has been downloaded
		new ACondition(30 * 1000) {
			@Override
			public boolean test() throws Exception {
				if (actualContentDownloadStates.size() == 4) {

					assertEquals(
							"Expected the first prefetching content download state to be: " + DownloadState.NOT_STARTED,
							actualContentDownloadStates.get(0), DownloadState.NOT_STARTED);

					assertEquals(
							"Expected the second prefetching content download state to be: "
									+ DownloadState.IS_DOWNLOADING,
							actualContentDownloadStates.get(1), DownloadState.IS_DOWNLOADING);
					assertEquals(
							"Expected the third prefetching content download state to be: "
									+ DownloadState.DOWNLOADING_COMPLETED,
							actualContentDownloadStates.get(2), DownloadState.DOWNLOADING_COMPLETED);
					assertEquals(
							"Expected the fourth prefetching content download state to be: " + DownloadState.DOWNLOADED,
							actualContentDownloadStates.get(3), DownloadState.DOWNLOADED);

					// Final state must be "post downloaded" state
					assertEquals(
							"Expected final download state for content provider properties to be: "
									+ DownloadState.DOWNLOADED,
							DownloadState.DOWNLOADED,
							contentManager.getPrefetchContentProviderPropertiesTracker().getValue());

					assertEquals("Expected final download state for content to be: " + DownloadState.DOWNLOADED,
							DownloadState.DOWNLOADED, contentManager.getPrefetchContentTracker().getValue());

					ContentType<?>[] types = contentManager.getTypes();
					assertTrue("Expected content types for getting started guides after downloading completed.",
							types != null && types.length > 0);

					List<?> allContent = getContent(types, contentManager);
					assertTrue("Expected content for getting started guides after downloading completed.",
							allContent.size() > 0);

					return true;
				} else {
					return false;
				}
			}
		};
	}

	public static GSImportWizardModel getModel() throws Exception {
		return new GSImportWizardModel();
	}

	public static IRunnableContext getRunnableContext() {
		return JobUtil.DEFAULT_BACKGROUND_RUNNABLE_CONTEXT;
	}

	protected List<?> getContent(ContentType<?>[] types, ContentManager contentManager) {
		List<Object> content = new ArrayList<>();

		for (ContentType<?> type : types) {
			Object[] typeContent = contentManager.get(type);
			content.addAll(Arrays.asList(typeContent));
		}

		return content;
	}
}
