/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.tests.build;

import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.causes.CIEventCauseType;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.utils.CIPluginSDKUtils;
import com.hp.octane.integrations.utils.SdkConstants;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import com.microfocus.application.automation.tools.octane.model.CIEventCausesFactory;
import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import hudson.FilePath;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic utilities handling Job/Run metadata extraction/transformation/processing
 */

public class BuildHandlerUtils {
	private static final Logger logger = SDKBasedLoggerProvider.getLogger(BuildHandlerUtils.class);
	public static final String JOB_LEVEL_SEPARATOR = "/job/";

	public static BuildDescriptor getBuildType(Run<?, ?> run) {
		for (BuildHandlerExtension ext : BuildHandlerExtension.all()) {
			if (ext.supports(run)) {
				return ext.getBuildType(run);
			}
		}
		return new BuildDescriptor(
				BuildHandlerUtils.getJobCiId(run),
				run.getParent().getName(),
				BuildHandlerUtils.getBuildCiId(run),
				String.valueOf(run.getNumber()),
				"");
	}

	@Deprecated
	public static String getProjectFullName(Run<?, ?> run) {
		for (BuildHandlerExtension ext : BuildHandlerExtension.all()) {
			if (ext.supports(run)) {
				return ext.getProjectFullName(run);
			}
		}
		return run.getParent().getFullName();
	}

	public static FilePath getWorkspace(Run<?, ?> run) {
		if (run.getExecutor() != null && run.getExecutor().getCurrentWorkspace() != null) {
			return run.getExecutor().getCurrentWorkspace();
		}
		if (run instanceof AbstractBuild) {
			return ((AbstractBuild) run).getWorkspace();
		}
		if (run instanceof WorkflowRun) {
			FlowExecution fe = ((WorkflowRun) run).getExecution();
			if (fe != null) {
				FlowGraphWalker w = new FlowGraphWalker(fe);
				for (FlowNode n : w) {
					WorkspaceAction action = n.getAction(WorkspaceAction.class);
					if (action != null) {
						FilePath workspace = action.getWorkspace();
						if (workspace == null) {
							workspace = handleWorkspaceActionWithoutWorkspace(action);
						}
						return workspace;
					}
				}
			}
		}

		logger.error("BuildHandlerUtils.getWorkspace - run is not handled. Run type : " + run.getClass());
		return null;
	}

	private static FilePath handleWorkspaceActionWithoutWorkspace(WorkspaceAction action) {
		logger.error("Found WorkspaceAction without workspace");
		logger.warn("Node getPath = " + action.getPath());
		logger.warn("Node getNode = " + action.getNode());
		FilePath workspace = null;

		if (StringUtils.isNotEmpty(action.getPath())) {
			logger.warn("Node getPath is not empty, return getPath as workspace");
			workspace = new FilePath(new File(action.getPath()));
		} else {
			logger.warn("Node getPath is empty, return workspace = null");
		}
		return workspace;
	}

	public static String getBuildCiId(Run run) {
		return String.valueOf(run.getNumber());
		//  YG  temporary disabling the support for fluid build number until Octane supports it
		//return run.getNumber() + "_" + run.getStartTimeInMillis();
	}

	public static String getJobCiId(Run run) {
		if (run.getParent() instanceof MatrixConfiguration) {
			return JobProcessorFactory.getFlowProcessor(((MatrixRun) run).getProject()).getTranslatedJobName();
		}
		if (run.getParent().getClass().getName().equals(JobProcessorFactory.WORKFLOW_JOB_NAME)) {
			return JobProcessorFactory.getFlowProcessor(run.getParent()).getTranslatedJobName();
		}
		return JobProcessorFactory.getFlowProcessor(((AbstractBuild) run).getProject()).getTranslatedJobName();
	}

	public static String translateFolderJobName(String jobPlainName) {
		return jobPlainName.replaceAll("/", JOB_LEVEL_SEPARATOR);
	}

	public static String revertTranslateFolderJobName(String translatedJobName) {
		return translatedJobName.replaceAll(JOB_LEVEL_SEPARATOR, "/");
	}

	public static String translateFullDisplayName(String fullDisplayName) {
		return fullDisplayName.replaceAll(" » ", "/");
	}

	public static CIBuildResult translateRunResult(Run run) {
		CIBuildResult result;
		if (run.getResult() == Result.SUCCESS) {
			result = CIBuildResult.SUCCESS;
		} else if (run.getResult() == Result.ABORTED) {
			result = CIBuildResult.ABORTED;
		} else if (run.getResult() == Result.FAILURE) {
			result = CIBuildResult.FAILURE;
		} else if (run.getResult() == Result.UNSTABLE) {
			result = CIBuildResult.UNSTABLE;
		} else {
			result = CIBuildResult.UNAVAILABLE;
		}
		return result;
	}

	public static boolean isWorkflowStartNode(FlowNode node) {
		return node.getParents().isEmpty() ||
				node.getParents().stream().anyMatch(fn -> fn instanceof FlowStartNode);
	}

	public static boolean isWorkflowEndNode(FlowNode node) {
		return node instanceof FlowEndNode;
	}

	public static boolean isStageStartNode(FlowNode node) {
		return node instanceof StepStartNode && node.getAction(LabelAction.class) != null && node.getAction(ThreadNameAction.class) == null;
	}

	public static boolean isStageEndNode(FlowNode node) {
		return node instanceof StepEndNode && isStageStartNode(((StepEndNode) node).getStartNode());
	}

	public static WorkflowRun extractParentRun(FlowNode flowNode) {
		try {
			return (WorkflowRun) flowNode.getExecution().getOwner().getExecutable();
		} catch (IOException ioe) {
			logger.error("failed to extract parent workflow run from " + flowNode, ioe);
			throw new IllegalStateException("failed to extract parent workflow run from " + flowNode);
		}
	}

	public static String getRootJobCiIds(Run<?, ?> run) {
		Set<String> parents = new HashSet<>();
		CIPluginSDKUtils.getRootJobCiIds(BuildHandlerUtils.getJobCiId(run), CIEventCausesFactory.processCauses(run), parents);
		return String.join(SdkConstants.General.JOB_PARENT_DELIMITER, parents);
	}
}
