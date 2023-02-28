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

package com.microfocus.application.automation.tools.octane.configuration;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.apache.logging.log4j.Logger;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/*
    Utility to help retrieving the configuration of the SSC Server URL and SSC project/version pair
 */

public class SSCServerConfigUtil {
	private static final Logger logger = SDKBasedLoggerProvider.getLogger(SSCServerConfigUtil.class);
	private static final String PUBLISHER_NEW_NAME = "com.fortify.plugin.jenkins.FortifyPlugin";
	private static final String PUBLISHER_OLD_VERSION = "com.fortify.plugin.jenkins.FPRPublisher";

	private static final String FORTIFY_UPLOAD_ACTION_NAME = "com.fortify.plugin.jenkins.FortifyUploadBuildAction";
	private static final String FORTIFY_UPLOAD_PROJECT_ACTIONS_METHOD = "getProjectActions";
	private static final String FORTIFY_UPLOAD_APP_NAME_METHOD = "getAppName";
	private static final String FORTIFY_UPLOAD_APP_VERSION_METHOD = "getAppVersion";

	public static String getSSCServer() {
		Descriptor sscDescriptor = getSSCDescriptor();
		return sscDescriptor != null ? getFieldValue(sscDescriptor, "url") : null;
	}

	/**
	 * extracts and returns SSC project name and version if found on the AbstractProject configuration of the specified build
	 *
	 * @param build AbstractBuild
	 * @return valid SSC project name and version pair; otherwise NULL
	 */
	public static SSCProjectVersionPair getProjectConfigurationFromBuild(AbstractBuild build) {
		return build != null ? getProjectVersion(build.getProject()) : null;
	}

	public static SSCProjectVersionPair getProjectConfigurationFromWorkflowRun(WorkflowRun run) {

		SSCProjectVersionPair projectVersionPair = null;
		List<Action> workflowActions = run != null ? (List<Action>) run.getAllActions() : new ArrayList<>();

		for (Action action: workflowActions) {
			if (action.getClass().getName().equals(FORTIFY_UPLOAD_ACTION_NAME)) {
				try {
					List<Action> projectActions = (List<Action>) ReflectionUtils.invokeMethodByName(action, FORTIFY_UPLOAD_PROJECT_ACTIONS_METHOD, null);
					Action projectMethods = projectActions != null && projectActions.size() > 0 ? projectActions.get(0) : null;

					if (projectMethods != null) {
						String projName = (String) ReflectionUtils.invokeMethodByName(projectMethods, FORTIFY_UPLOAD_APP_NAME_METHOD, null);
						String version = (String) ReflectionUtils.invokeMethodByName(projectMethods, FORTIFY_UPLOAD_APP_VERSION_METHOD, null);

						projectVersionPair = new SSCProjectVersionPair(projName, version);
					}
				} catch(Exception e) {
					logger.error("Failed getProjectConfigurationFromWorkflowRun", e);
				}
			}
		}

		return projectVersionPair;
	}

	private static SSCProjectVersionPair getProjectVersion(AbstractProject project) {
		for (Object publisher : project.getPublishersList()) {
			if (publisher instanceof Publisher &&
					isSSCPublisher(publisher.getClass().getName())) {
				return getProjectNameByReflection(publisher);
			}
		}
		return null;
	}

	private static boolean isSSCPublisher(String publisherName) {
		return PUBLISHER_NEW_NAME.equals(publisherName) ||
				PUBLISHER_OLD_VERSION.equals(publisherName);
	}

	private static SSCProjectVersionPair getProjectNameByReflection(Object fprPublisher) {
		String projectName = getFieldValue(fprPublisher, "projectName");
		String projectVersion = getFieldValue(fprPublisher, "projectVersion");
		if (projectName != null && !projectName.isEmpty() && projectVersion != null && !projectVersion.isEmpty()) {
			return new SSCProjectVersionPair(projectName, projectVersion);
		}
		logger.warn("Version seems to be 18.20.1071 or higher");
		//18.20.1071 version.
		Object uploadSSC = getFieldValueAsObj(fprPublisher, "uploadSSC");
		if (uploadSSC == null) {
			Object analysisRunType = getFieldValueAsObj(fprPublisher, "analysisRunType");
			if (analysisRunType == null) {
				logger.warn("uploadSSC section was not found");
			} else {
				uploadSSC = getFieldValueAsObj(analysisRunType, "uploadSSC");
				if (uploadSSC == null) {
					logger.warn("uploadSSC section was not found");
				} else {
					projectName = getFieldValue(uploadSSC, "appName");
					projectVersion = getFieldValue(uploadSSC, "appVersion");
					if (projectName != null && !projectName.isEmpty() && projectVersion != null && !projectVersion.isEmpty()) {
						return new SSCProjectVersionPair(projectName, projectVersion);
					}
				}

				logger.warn("uploadSSC section was not found");
			}
		} else {
			logger.warn("uploadSSC was found ");
			projectName = getFieldValue(uploadSSC, "projectName");
			projectVersion = getFieldValue(uploadSSC, "projectVersion");
			logger.warn("projectName" + projectName + " , ProjectVersion" + projectVersion);
		}
		if (projectName != null && !projectName.isEmpty() && projectVersion != null && !projectVersion.isEmpty()) {
			return new SSCProjectVersionPair(projectName, projectVersion);
		}
		return null;
	}

	private static String getFieldValue(Object someObject, String fieldName) {
		for (Field field : someObject.getClass().getDeclaredFields()) {
			field.setAccessible(true);
			if (field.getName().equals(fieldName)) {
				Object value = null;
				try {
					value = field.get(someObject);
				} catch (IllegalAccessException e) {
					logger.error("Failed to getFieldValue", e);
				}
				if (value != null) {
					return value.toString();
				}
			}
		}
		return null;
	}

	private static Object getFieldValueAsObj(Object someObject, String fieldName) {
		for (Field field : someObject.getClass().getDeclaredFields()) {
			field.setAccessible(true);
			if (field.getName().equals(fieldName)) {
				try {
					return field.get(someObject);
				} catch (IllegalAccessException e) {
					logger.error("Failed to getFieldValue", e);
				}
			}
		}
		return null;
	}

	private static Descriptor getSSCDescriptor() {
		Descriptor publisher = Jenkins.get().getDescriptorByName(PUBLISHER_OLD_VERSION);
		if (publisher == null) {
			//18.20 version and above.
			logger.debug("didn't find Old SSC FPRPublisher");
			Descriptor plugin = Jenkins.get().getDescriptorByName(PUBLISHER_NEW_NAME);
			if (plugin == null) {
				logger.debug("didn't find Fortify Plugin of 18.20 version and above");
			}
			return plugin;
		}
		return publisher;
	}

	public static final class SSCProjectVersionPair {
		public final String project;
		public final String version;

		public SSCProjectVersionPair(String project, String version) {
			this.project = project;
			this.version = version;
		}
	}
}
