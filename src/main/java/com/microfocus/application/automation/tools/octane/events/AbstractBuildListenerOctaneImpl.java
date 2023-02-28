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

package com.microfocus.application.automation.tools.octane.events;

import com.google.inject.Inject;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.microfocus.application.automation.tools.octane.CIJenkinsServicesImpl;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import com.microfocus.application.automation.tools.octane.model.CIEventCausesFactory;
import com.microfocus.application.automation.tools.octane.model.processors.parameters.ParameterProcessors;
import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import com.microfocus.application.automation.tools.octane.model.processors.scm.CommonOriginRevision;
import com.microfocus.application.automation.tools.octane.model.processors.scm.SCMProcessor;
import com.microfocus.application.automation.tools.octane.model.processors.scm.SCMProcessors;
import com.microfocus.application.automation.tools.octane.tests.TestListener;
import com.microfocus.application.automation.tools.octane.tests.build.BuildHandlerUtils;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Run Listener that handles basic CI events and dispatches notifications to the Octane server
 * User: gullery
 * Date: 24/08/14
 * Time: 17:21
 */

@Extension
@SuppressWarnings({"squid:S2259", "squid:S1872", "squid:S1698", "squid:S1132"})
public final class AbstractBuildListenerOctaneImpl extends RunListener<AbstractBuild> {
	private static final Logger logger = SDKBasedLoggerProvider.getLogger(AbstractBuildListenerOctaneImpl.class);
	private static final DTOFactory dtoFactory = DTOFactory.getInstance();

	@Inject
	private TestListener testListener;

	@Override
	public void onStarted(AbstractBuild build, TaskListener listener) {
		if(!OctaneSDK.hasClients()){
			return;
		}
		publishStartEvent(build);
	}

	private void publishStartEvent(AbstractBuild build) {
		try {
			CIEvent event = dtoFactory.newDTO(CIEvent.class)
					.setEventType(CIEventType.STARTED)
					.setProject(BuildHandlerUtils.getJobCiId(build))
					.setProjectDisplayName(BuildHandlerUtils.translateFullDisplayName(build.getParent().getFullDisplayName()))
					.setBuildCiId(BuildHandlerUtils.getBuildCiId(build))
					.setNumber(String.valueOf(build.getNumber()))
					.setStartTime(build.getStartTimeInMillis())
					.setEstimatedDuration(build.getEstimatedDuration())
					.setCauses(CIEventCausesFactory.processCauses(build))
					.setParameters(ParameterProcessors.getInstances(build));
			if (isInternal(build)) {
				event.setPhaseType(PhaseType.INTERNAL);
			} else {
				event.setPhaseType(PhaseType.POST);
			}
			CIJenkinsServicesImpl.publishEventToRelevantClients(event);
		} catch (Throwable throwable) {
			logger.error("failed to build and/or dispatch STARTED event for " + build, throwable);
		}
	}

	@Override
	public void onFinalized(AbstractBuild build) {
		if(!OctaneSDK.hasClients()){
			return;
		}
		publishFinishEvent(build);
		BuildLogHelper.enqueueBuildLog(build);
	}

	private void publishFinishEvent(AbstractBuild build) {
		try {
			boolean hasTests = testListener.processBuild(build);
			CIEvent event = dtoFactory.newDTO(CIEvent.class)
					.setEventType(CIEventType.FINISHED)
					.setProject(BuildHandlerUtils.getJobCiId(build))
					.setProjectDisplayName((BuildHandlerUtils.translateFullDisplayName(build.getParent().getFullDisplayName())))
					.setBuildCiId(BuildHandlerUtils.getBuildCiId(build))
					.setNumber(String.valueOf(build.getNumber()))
					.setStartTime(build.getStartTimeInMillis())
					.setEstimatedDuration(build.getEstimatedDuration())
					.setCauses(CIEventCausesFactory.processCauses(build))
					.setParameters(ParameterProcessors.getInstances(build))
					.setResult(BuildHandlerUtils.translateRunResult(build))
					.setDuration(build.getDuration())
					.setTestResultExpected(hasTests)
					.setEnvironmentOutputtedParameters(OutputEnvironmentParametersHelper.getOutputEnvironmentParams(build));
			CommonOriginRevision commonOriginRevision = getCommonOriginRevision(build);
			if (commonOriginRevision != null) {
				event
						.setCommonHashId(commonOriginRevision.revision)
						.setBranchName(commonOriginRevision.branch);
			}
			CIJenkinsServicesImpl.publishEventToRelevantClients(event);
		} catch (Throwable throwable) {
			logger.error("failed to build and/or dispatch FINISHED event for " + build, throwable);
		}
	}

	private CommonOriginRevision getCommonOriginRevision(AbstractBuild build) {
		CommonOriginRevision commonOriginRevision = null;
		SCM scm = build.getProject().getScm();
		if (scm != null) {
			SCMProcessor scmProcessor = SCMProcessors.getAppropriate(scm.getClass().getName());
			if (scmProcessor != null) {
				commonOriginRevision = scmProcessor.getCommonOriginRevision(build);
			}
		}
		return commonOriginRevision;
	}

	//  TODO: https://issues.jenkins-ci.org/browse/JENKINS-53410
	private boolean isInternal(Run r) {
		boolean result = false;

		//  get upstream cause, if any
		Cause.UpstreamCause upstreamCause = null;
		for (Object cause : r.getCauses()) {
			if (cause instanceof Cause.UpstreamCause) {
				upstreamCause = (Cause.UpstreamCause) cause;
				break;
			}
		}

		if (upstreamCause != null) {
			String causeJobName = upstreamCause.getUpstreamProject();
			Item parent = Jenkins.get().getItemByFullName(causeJobName);
			if (parent == null) {
				result = true;
			} else {
				if (parent.getClass().getName().equals(JobProcessorFactory.WORKFLOW_JOB_NAME)) {
					result = true;
				} else {
					List<PipelinePhase> phases = JobProcessorFactory.getFlowProcessor((Job) parent).getInternals();
					for (PipelinePhase p : phases) {
						for (PipelineNode n : p.getJobs()) {
							if (n != null && n.getName().equals(r.getParent().getName())) {
								return true;
							}
						}
					}
					return false;
				}
			}
		}
		return result;
	}
}
