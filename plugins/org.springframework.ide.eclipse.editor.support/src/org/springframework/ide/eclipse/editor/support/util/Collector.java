/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link IRequestor} that simplies stores all items received into
 * a List
 *
 * @author Kris De Volder
 */
public class Collector<T> implements IRequestor<T> {

	@SuppressWarnings("unchecked")
	private List<T> nodes = Collections.EMPTY_LIST;

	@Override
	public void accept(T node) {
		if (nodes==Collections.EMPTY_LIST) {
			nodes = new ArrayList<T>();
		}
		nodes.add(node);
	}

	public List<T> get() {
		return nodes;
	}
}
