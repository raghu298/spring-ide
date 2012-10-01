/*******************************************************************************
 *  Copyright (c) 2012 VMware, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.wizard.template.infrastructure.processor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * @author Terry Denney
 * @author Leo Dos Santos
 * @author Christian Dupuis
 * @author Kaitlin Duck Sherwood
 */
public class TemplateProcessor {
	private static Logger logger = Logger.getLogger(TemplateProcessor.class);

	protected Map<String, String> replacementContext = new HashMap<String, String>();

	public TemplateProcessor(Map<String, String> replacementContext) {
		this.replacementContext = replacementContext;
	}

	public void process(File source, File target) throws IOException {
		if (FileUtil.isBinaryFile(source)) {
			logger.debug("Copying binary file " + source);
			FileUtil.copy(source, target);
		}
		else {
			logger.debug("Template processing text file " + source);
			process(new FileReader(source), new FileWriter(target));
		}
	}

	public void process(Reader reader, Writer writer) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(reader);
		BufferedWriter bufferedWriter = new BufferedWriter(writer);

		while (true) {
			String line = bufferedReader.readLine();
			if (line == null) {
				break;
			}
			line = replaceTokens(line);
			bufferedWriter.write(line);
			bufferedWriter.write('\n');
		}
		bufferedWriter.flush();
	}

	public String replaceTokens(String input) {
		String topLevelPackageStr = replacementContext.get("||top-level-package||");
		if (topLevelPackageStr != null) {
			if (topLevelPackageStr.startsWith("&&")) {
				topLevelPackageStr = topLevelPackageStr.substring(2);
			}
			topLevelPackageStr = topLevelPackageStr.replaceAll("&&", ".");
			String userTopLevelPackageStr = replacementContext.get("||user-top-level-package||");
			if (userTopLevelPackageStr.startsWith("&&")) {
				userTopLevelPackageStr = userTopLevelPackageStr.substring(2);
			}

			if (input.contains(topLevelPackageStr) && userTopLevelPackageStr != null) {
				int index = input.indexOf(topLevelPackageStr);
				String newInput = input.substring(0, index) + userTopLevelPackageStr.replaceAll("&&", ".");

				if (index + topLevelPackageStr.length() < input.length()) {
					newInput += input.substring(index + topLevelPackageStr.length());
				}

				input = newInput;
			}
		}

		for (String token : replacementContext.keySet()) {
			String replacement = replacementContext.get(token);
			input = input.replace(token, replacement);
		}
		if (input.indexOf("my") >= 0) {
			logger.warn("May have failed to replace token " + input);
		}
		return input;
	}

	public String replacePathForDirectory(String path, char separator) {
		String originalTopLevelPackageStr = replacementContext.get("||top-level-package||");
		if (originalTopLevelPackageStr != null) {
			originalTopLevelPackageStr = originalTopLevelPackageStr.replaceAll("&&", "\\" + separator);

			String userTopLevelPackageStr = replacementContext.get("||user-top-level-package||");

			if (path.contains(originalTopLevelPackageStr) && userTopLevelPackageStr != null) {
				int index = path.indexOf(originalTopLevelPackageStr);
				String replacementString = userTopLevelPackageStr.replaceAll("&&", "\\" + separator);
				String newPath = path.substring(0, index) + replacementString;

				if (index + 1 + originalTopLevelPackageStr.length() < path.length()) {
					newPath += path.substring(index + originalTopLevelPackageStr.length());
				}

				return newPath;
			}
		}

		return null;
	}
}