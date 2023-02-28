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

package com.microfocus.application.automation.tools.octane.model.processors.projects;

import hudson.model.Item;
import hudson.model.Job;

/**
 * Created by gadiel on 30/11/2016.
 * <p>
 * Job processors factory - should be used as a 'static' class, no instantiation, only static method/s
 */

public class JobProcessorFactory {
	//  native
	public static final String FREE_STYLE_JOB_NAME = "hudson.model.FreeStyleProject";
	public static final String SIMPLE_BUILD_TRIGGER = "hudson.tasks.BuildTrigger";
	public static final String PARAMETRIZED_BUILD_TRIGGER = "hudson.plugins.parameterizedtrigger.BuildTrigger";
	public static final String PARAMETRIZED_TRIGGER_BUILDER = "hudson.plugins.parameterizedtrigger.TriggerBuilder";

	//  workflow
	public static final String WORKFLOW_JOB_NAME = "org.jenkinsci.plugins.workflow.job.WorkflowJob";
	public static final String WORKFLOW_RUN_NAME = "org.jenkinsci.plugins.workflow.job.WorkflowRun";
	public static final String WORKFLOW_MULTI_BRANCH_JOB_NAME = "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";

	//  multijob
	public static final String MULTIJOB_JOB_NAME = "com.tikal.jenkins.plugins.multijob.MultiJobProject";
	public static final String MULTIJOB_BUILDER = "com.tikal.jenkins.plugins.multijob.MultiJobBuilder";

	//  matrix
	public static final String MATRIX_JOB_NAME = "hudson.matrix.MatrixProject";
	public static final String MATRIX_CONFIGURATION_NAME = "hudson.matrix.MatrixConfiguration";

	//  conditional
	public static final String CONDITIONAL_BUILDER_NAME = "org.jenkinsci.plugins.conditionalbuildstep.ConditionalBuilder";
	public static final String SINGLE_CONDITIONAL_BUILDER_NAME = "org.jenkinsci.plugins.conditionalbuildstep.singlestep.SingleConditionalBuilder";

	//  maven
	public static final String MAVEN_JOB_NAME = "hudson.maven.MavenModuleSet";
	public static final String MAVEN_MODULE_NAME = "hudson.maven.MavenModule";

	//  folders
	public static final String FOLDER_JOB_NAME = "com.cloudbees.hudson.plugins.folder.Folder";
	public static final String GITHUB_ORGANIZATION_FOLDER = "jenkins.branch.OrganizationFolder";

	private JobProcessorFactory() {
	}

	public static <T extends Job> AbstractProjectProcessor<T> getFlowProcessor(T job) {
		AbstractProjectProcessor flowProcessor;

		switch (job.getClass().getName()) {
			case FREE_STYLE_JOB_NAME:
				flowProcessor = new FreeStyleProjectProcessor(job);
				break;
			case MATRIX_JOB_NAME:
				flowProcessor = new MatrixProjectProcessor(job);
				break;
			case MATRIX_CONFIGURATION_NAME:
				flowProcessor = new MatrixConfigurationProcessor(job);
				break;
			case MAVEN_JOB_NAME:
				flowProcessor = new MavenProjectProcessor(job);
				break;
			case MULTIJOB_JOB_NAME:
				flowProcessor = new MultiJobProjectProcessor(job);
				break;
			case WORKFLOW_JOB_NAME:
				flowProcessor = new WorkFlowJobProcessor(job);
				break;
			default:
				flowProcessor = new UnsupportedProjectProcessor(job);
				break;
		}

		return flowProcessor;
	}

	public static  boolean isFolder(Item item) {
		return JobProcessorFactory.FOLDER_JOB_NAME.equals(item.getClass().getName());
	}

	public static boolean isMultibranch(Item item) {
		return JobProcessorFactory.WORKFLOW_MULTI_BRANCH_JOB_NAME.equals(item.getClass().getName());
	}

	public static boolean isJob(Item item) {
		return item instanceof Job;
	}

	public static boolean isMultibranchChild(Item item) {
		return JobProcessorFactory.WORKFLOW_JOB_NAME.equals(item.getClass().getName()) &&
				JobProcessorFactory.WORKFLOW_MULTI_BRANCH_JOB_NAME.equals(item.getParent().getClass().getName());
	}
}
