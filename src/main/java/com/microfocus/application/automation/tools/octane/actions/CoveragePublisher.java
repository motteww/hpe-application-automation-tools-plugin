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

package com.microfocus.application.automation.tools.octane.actions;

import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.coverage.CoverageReportType;
import com.microfocus.application.automation.tools.octane.Messages;
import com.microfocus.application.automation.tools.octane.actions.coverage.CoveragePublisherAction;
import com.microfocus.application.automation.tools.octane.actions.coverage.CoverageService;
import com.microfocus.application.automation.tools.octane.tests.build.BuildHandlerUtils;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * Post-build action that collects the coverage reports from workspace
 * the reports that matches a specified regular expression path, are copied to
 * the build folder for future upload.
 */
public class CoveragePublisher extends Recorder implements SimpleBuildStep {
	private final String jacocoPathPattern;
	private final String lcovPathPattern;

	/**
	 * this ctor is being called from configuration page.
	 * the jacocoPathPattern is being injected from the web page text box
	 *
	 * @param jacocoPathPattern regular expression path for coverage reports
	 */
	@DataBoundConstructor
	public CoveragePublisher(String jacocoPathPattern, String lcovPathPattern) {
		// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
		this.jacocoPathPattern = jacocoPathPattern == null || jacocoPathPattern.isEmpty() ? CoverageService.Jacoco.JACOCO_DEFAULT_PATH : jacocoPathPattern;
		this.lcovPathPattern = lcovPathPattern == null || lcovPathPattern.isEmpty() ? CoverageService.Lcov.LCOV_DEFAULT_PATH : lcovPathPattern;
	}

	/**
	 * this method used for serialization & deserialization of path
	 *
	 * @return jacoco path
	 */
	public String getJacocoPathPattern() {
		return jacocoPathPattern;
	}

	/**
	 * this method used for serialization & deserialization of path
	 *
	 * @return lcov path
	 */
	public String getLcovPathPattern() {
		return lcovPathPattern;
	}

	/**
	 * this is where we build the project. this method is being called when we run the build
	 *
	 * @param run    instance
	 * @param filePath instance
	 * @param launcher for action attachment
	 * @param taskListener for action attachment
	 */
	@Override
	public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
		boolean isSuccessful = perform(run, taskListener);
		if (!isSuccessful) {
			run.setResult(Result.FAILURE);
		}
	}

	/**
	 * this is where we build the project. this method is being called when we run the build
	 *
	 * @param build    instance
	 * @param launcher instance
	 * @param listener for action attachment
	 * @return status
	 */
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		return perform(build, listener);
	}

	public boolean perform(Run build, TaskListener listener) {
		CoveragePublisherAction action = new CoveragePublisherAction(build, listener);
		build.addAction(action);

		List<String> jacocoReportFileNames = action.copyCoverageReportsToBuildFolder(jacocoPathPattern, CoverageService.Jacoco.JACOCO_DEFAULT_FILE_NAME);
		List<String> lcovReportFileNames = action.copyCoverageReportsToBuildFolder(lcovPathPattern, CoverageService.Lcov.LCOV_DEFAULT_FILE_NAME);
		boolean copyReportsToBuildFolderStatus = enqueueReports(build, jacocoReportFileNames, CoverageReportType.JACOCOXML) ||
				enqueueReports(build, lcovReportFileNames, CoverageReportType.LCOV);

		return copyReportsToBuildFolderStatus;
	}

	private boolean enqueueReports(Run build, List<String> reportFileNames, CoverageReportType coverageReportType){
		if (!reportFileNames.isEmpty()) {
			String parents = BuildHandlerUtils.getRootJobCiIds(build);
			OctaneSDK.getClients().forEach(octaneClient-> {
				for (String reportFileName : reportFileNames) {
					octaneClient.getCoverageService()
							.enqueuePushCoverage(BuildHandlerUtils.getJobCiId(build), String.valueOf(build.getNumber()), coverageReportType, reportFileName, parents);
				}
			});
			return true;
		}
		return false;
	}

	/**
	 * bound between descriptor to publisher
	 *
	 * @return descriptor
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Returns BuildStepMonitor.NONE by default, as Builders normally don't depend on its previous result
	 *
	 * @return monitor
	 */
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * The Publisher object or Recorder is the base.
	 * It needs a BuildStepDescriptor to provide certain information to Jenkins
	 */
	@Symbol("publishCodeCoverage")
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		public DescriptorImpl() {
			load();
		}

		/**
		 * Indicates that this builder can be used with all kinds of project types
		 *
		 * @param aClass that describe the job
		 * @return always true, indicate that this post build action suitable for all jenkins jobs
		 */
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true; // so that it will also be available for maven & other projects
		}

		public String getDisplayName() {
			return "ALM Octane code coverage publisher";
		}

		public FormValidation doCheckJacocoPathPattern(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
			if (value == null || value.isEmpty()) {
				return FormValidation.warning(Messages.CoverageResultsActionEmptyConfigurationWarning(), CoverageService.Jacoco.JACOCO_DEFAULT_PATH);
			} else if (project == null) {
				return FormValidation.ok();
			}
			return FilePath.validateFileMask(project.getSomeWorkspace(), value);
		}

		public FormValidation doCheckLcovPathPattern(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, ServletException {
			if (value == null || value.isEmpty()) {
				return FormValidation.warning(Messages.CoverageResultsActionEmptyConfigurationWarning(), CoverageService.Lcov.LCOV_DEFAULT_PATH);
			} else if (project == null) {
				return FormValidation.ok();
			}
			return FilePath.validateFileMask(project.getSomeWorkspace(), value);
		}
	}
}