/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudAppDashElement;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryBootDashModel;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFApplicationDetail;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.ClientRequests;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.v2.CFPushArguments;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.debug.DebugSupport;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment.CloudApplicationDeploymentProperties;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.LocalRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens.CancelationToken;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

public class ProjectsDeployer extends CloudOperation {

	private static final boolean DEBUG =
			(""+Platform.getLocation()).contains("bamboo") ||
			(""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	private final Set<IProject> projectsToDeploy;
	private final UserInteractions ui;
	private final RunState runOrDebug;
	private final DebugSupport debugSupport;

	public ProjectsDeployer(CloudFoundryBootDashModel model,
			UserInteractions ui,
			Set<IProject> projectsToDeploy,
			RunState runOrDebug,
			DebugSupport debugSupport) {
		super("Deploying projects", model);
		this.projectsToDeploy = projectsToDeploy;
		this.ui = ui;
		this.runOrDebug = runOrDebug;
		this.debugSupport = debugSupport;
	}

	protected void doCloudOp(IProgressMonitor monitor) throws Exception, OperationCanceledException {
		monitor.beginTask("Deploy projects", projectsToDeploy.size());
		try {
			for (Iterator<IProject> it = projectsToDeploy.iterator(); it.hasNext();) {
				IProject project = it.next();
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				deployProject(project, new SubProgressMonitor(monitor, 1));
			}
		} finally {
			monitor.done();
		}
	}

	private void deployProject(IProject project, IProgressMonitor monitor) throws Exception {
		debug("deployProject["+project.getName()+"] starting");
		CloudApplicationDeploymentProperties properties = model.createDeploymentProperties(project, ui, monitor);
		debug("deployProject["+project.getName()+"] got deployment properties");
		CloudAppDashElement cde = model.ensureApplication(properties.getAppName());
		debug("deployProject["+project.getName()+"] created cde: "+cde.getName());
		model.runAsynch("Deploy project '"+project.getName()+"'", properties.getAppName(), (IProgressMonitor progressMonitor) -> {
			doDeployProject(cde, properties, project, progressMonitor);
		}, ui);
	}

	protected void doDeployProject(CloudAppDashElement cde, CloudApplicationDeploymentProperties properties,
			IProject project, IProgressMonitor monitor) throws Exception {
		ClientRequests client = model.getRunTarget().getClient();
		CancelationToken cancelationToken = cde.createCancelationToken();


		try {
			cde.whileStarting(ui, cancelationToken, monitor, () -> {
				if (client.applicationExists(properties.getAppName())) {
					CFApplicationDetail existingAppDetails = client.getApplication(properties.getAppName());

					if (!confirmOverwriteExisting(properties, existingAppDetails)) {
						throw new OperationCanceledException();
					}
				}
				cde.setDeploymentManifestFile(properties.getManifestFile());
				cde.setProject(project);
				copyTags(project, cde);
				cde.print("Pushing project '"+project.getName()+"'");
				try (CFPushArguments args = properties.toPushArguments(model.getCloudDomains(monitor))) {
					if (isDebugEnabled()) {
						debugSupport.setupEnvVars(args.getEnv());
					}
					client.push(args, CancelationTokens.merge(cancelationToken, monitor));
					cde.print("Pushing project '"+project.getName()+"' SUCCEEDED!");
				}
				if (cde.refresh()!=null) {
					//Careful... connecting the debugger must be done after the refresh because it needs the app guid which
					// won't be available for a newly created element if its not yet been populated with data from CF.
					if (isDebugEnabled()) {
						debugSupport.createOperation(cde, "Connect Debugger for "+cde.getName() , ui, cancelationToken).runOp(monitor);
					}
				}
			});
		} catch (Exception e) {
			cde.refresh();
			cde.printError("Pushing FAILED!");
			if (!ExceptionUtil.isCancelation(e)) {
				Log.log(e);
				if (ui != null) {
					String message = e.getMessage() != null && e.getMessage().trim().length() > 0 ? e.getMessage()
							: "Error type: " + e.getClass().getName()
									+ ". Check Error Log view for further details.";
					ui.errorPopup("Operation Failure", message);
				}
			}
		}
	}

	private boolean isDebugEnabled() {
		return runOrDebug==RunState.DEBUGGING && debugSupport!=null;
	}

	private void copyTags(IProject project, CloudAppDashElement cde) {
		BootDashElement localElement = findLocalBdeForProject(project);
		if (localElement!=null) {
			copyTags(localElement, cde);
		}
	}

	private BootDashElement findLocalBdeForProject(IProject project) {
		BootDashModel localModel = model.getViewModel().getSectionByTargetId(LocalRunTarget.INSTANCE.getId());
		if (localModel != null) {
			for (BootDashElement bde : localModel.getElements().getValue()) {
				if (project.equals(bde.getProject())) {
					return bde;
				}
			}
		}
		return null;
	}

	private static void copyTags(BootDashElement sourceBde, BootDashElement targetBde) {
		LinkedHashSet<String> tagsToCopy = sourceBde.getTags();
		if (tagsToCopy != null && !tagsToCopy.isEmpty()) {
			LinkedHashSet<String> targetTags = targetBde.getTags();
			for (String tag : tagsToCopy) {
				targetTags.add(tag);
			}
			targetBde.setTags(targetTags);
		}
	}

	private boolean confirmOverwriteExisting(CloudApplicationDeploymentProperties properties, CFApplicationDetail existingAppDetails) {
		String title = "Replace Existing Application";
		StringWriter writer = new StringWriter();
		writer.append("Replace existing application - ");
		writer.append(properties.getAppName());
		writer.append(" - with the project: ");
		writer.append(properties.getProject().getName());
		writer.append('?');
		return ui.confirmApplicationReplacement(title, writer.toString(), existingAppDetails);
	}
}