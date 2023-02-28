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

package com.microfocus.application.automation.tools.octane.tests.junit;

import com.google.inject.Inject;
import com.hp.octane.integrations.OctaneClient;
import com.hp.octane.integrations.OctaneSDK;
import com.microfocus.application.automation.tools.JenkinsUtils;
import com.microfocus.application.automation.tools.octane.actions.cucumber.CucumberTestResultsAction;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import com.microfocus.application.automation.tools.octane.executor.CheckOutSubDirEnvContributor;
import com.microfocus.application.automation.tools.octane.executor.UftConstants;
import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import com.microfocus.application.automation.tools.octane.tests.HPRunnerType;
import com.microfocus.application.automation.tools.octane.tests.OctaneTestsExtension;
import com.microfocus.application.automation.tools.octane.tests.TestResultContainer;
import com.microfocus.application.automation.tools.octane.tests.build.BuildHandlerUtils;
import com.microfocus.application.automation.tools.octane.tests.detection.MFToolsDetectionExtension;
import com.microfocus.application.automation.tools.octane.tests.detection.ResultFields;
import com.microfocus.application.automation.tools.octane.tests.detection.ResultFieldsDetectionService;
import com.microfocus.application.automation.tools.octane.tests.impl.ObjectStreamIterator;
import com.microfocus.application.automation.tools.settings.RunnerMiscSettingsGlobalConfiguration;
import hudson.Extension;
import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.test.AbstractTestResultAction;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static com.hp.octane.integrations.utils.SdkConstants.JobParameters.OCTANE_CONFIG_ID_PARAMETER_NAME;

/**
 * Converter of Jenkins test report to ALM Octane test report format(junitResult.xml->mqmTests.xml)
 */
@Extension
public class JUnitExtension extends OctaneTestsExtension {
	private static Logger logger = SDKBasedLoggerProvider.getLogger(JUnitExtension.class);

	private static final String JUNIT_RESULT_XML = "junitResult.xml"; // NON-NLS
	public static final String TEMP_TEST_RESULTS_FILE_NAME_PREFIX = "GetJUnitTestResults";
	private static final String TEST_RESULT_NAME_REGEX_PATTERN_PARAMETER_NAME = "octane_test_result_name_run_regex_pattern";

	@Inject
	private ResultFieldsDetectionService resultFieldsDetectionService;

	public boolean supports(Run<?, ?> build) {
		if (build.getAction(CucumberTestResultsAction.class) != null) {
			logger.debug("CucumberTestResultsAction found. Will not process JUnit results.");
			return false;
		} else if (build.getAction(AbstractTestResultAction.class) != null) {
			logger.debug("AbstractTestResultAction found, JUnit results expected");
			return true;
		} else {
			logger.debug("AbstractTestResultAction not found, no JUnit results expected");
			return false;
		}
	}

	@Override
	public TestResultContainer getTestResults(Run<?, ?> run, String jenkinsRootUrl) throws IOException, InterruptedException {
		logger.debug("Collecting JUnit results");

		FilePath resultFile = new FilePath(run.getRootDir()).child(JUNIT_RESULT_XML);
		boolean getResultsOnController = !RunnerMiscSettingsGlobalConfiguration.getInstance().isAgentToControllerEnabled();
		FilePath workspace = BuildHandlerUtils.getWorkspace(run);
		if (resultFile.exists()) {
			logger.debug("JUnit result report found");
			if (workspace == null) {
				logger.error("Received null workspace : " + run);
				return null;
			}


			HPRunnerType hpRunnerType = MFToolsDetectionExtension.getRunnerType(run);
			if(hpRunnerType.equals(HPRunnerType.UFT) || hpRunnerType.equals(HPRunnerType.UFT_MBT)){
				getResultsOnController = true;
			}
			FilePath filePath = getTestResultsFromWorkspace(run, jenkinsRootUrl, getResultsOnController, workspace, Collections.singletonList(resultFile),hpRunnerType);
			ResultFields detectedFields = getResultFields(run);
			return new TestResultContainer(new ObjectStreamIterator<>(filePath), detectedFields);
		} else {
			//avoid java.lang.NoClassDefFoundError when maven plugin is not present
			if ("hudson.maven.MavenModuleSetBuild".equals(run.getClass().getName())) {
				logger.debug("MavenModuleSetBuild detected, looking for results in maven modules");

				List<FilePath> resultFiles = new LinkedList<>();
				Map<MavenModule, MavenBuild> moduleLastBuilds = ((MavenModuleSetBuild) run).getModuleLastBuilds();
				for (MavenBuild mavenBuild : moduleLastBuilds.values()) {
					AbstractTestResultAction action = mavenBuild.getAction(AbstractTestResultAction.class);
					if (action != null) {
						FilePath moduleResultFile = new FilePath(mavenBuild.getRootDir()).child(JUNIT_RESULT_XML);
						if (moduleResultFile.exists()) {
							logger.debug("Found results in " + mavenBuild.getFullDisplayName());
							resultFiles.add(moduleResultFile);
						}
					}
				}
				if (!resultFiles.isEmpty()) {
					ResultFields detectedFields = getResultFields(run);
					FilePath filePath = getTestResultsFromWorkspace(run, jenkinsRootUrl, getResultsOnController, workspace, resultFiles,HPRunnerType.NONE);
					return new TestResultContainer(new ObjectStreamIterator<>(filePath), detectedFields);
				}
			}
			logger.debug("No JUnit result report found");
			return null;
		}
	}

	private FilePath getTestResultsFromWorkspace(Run<?, ?> run, String jenkinsRootUrl, boolean getResultsOnController, FilePath workspace, List<FilePath> resultFiles,HPRunnerType runnerType) throws IOException, InterruptedException {
		FilePath filePath;
		try {
			if (getResultsOnController) {
				logger.info("Get results from controller");
				filePath = (new GetJUnitTestResults(run, runnerType, resultFiles, false, jenkinsRootUrl)).invoke(null, null);
			} else {
				logger.info("Get results from agent");
				filePath = workspace.act(new GetJUnitTestResults(run, runnerType, resultFiles, false, jenkinsRootUrl));
			}
		}catch (Exception e){
			//if failed on controller/agent retrying from agent/controller
			logger.error(String.format("Failed to get test results from %s, trying to get test results from %s : %s",
						getResultsOnController ? "controller" : "agent",
						getResultsOnController ? "agent" : "controller",
						e.getMessage()),
					e);
			if (getResultsOnController) {
				logger.info("Get results from agent");
				filePath = workspace.act(new GetJUnitTestResults(run, runnerType, resultFiles, false, jenkinsRootUrl));
			} else {
				logger.info("Get results from controller");
				filePath = (new GetJUnitTestResults(run, runnerType, resultFiles, false, jenkinsRootUrl)).invoke(null, null);
			}
		}
		return filePath;
	}

	private ResultFields getResultFields(Run<?, ?> build) throws InterruptedException {
		return resultFieldsDetectionService.getDetectedFields(build);
	}

	private static class GetJUnitTestResults implements FilePath.FileCallable<FilePath> {

		private final List<FilePath> reports;
		private final String jobName;
		private final String buildId;
		private final String jenkinsRootUrl;
		private final HPRunnerType hpRunnerType;
		private FilePath filePath;
		private List<ModuleDetection> moduleDetection;
		private long buildStarted;
		private FilePath workspace;
		private boolean stripPackageAndClass;
		private String sharedCheckOutDirectory;
		private Pattern testParserRegEx;
		private boolean octaneSupportsSteps;

		//this class is run on master and JUnitXmlIterator is runnning on slave.
		//this object pass some master2slave data
		private Object additionalContext;
		private String nodeName;

		public GetJUnitTestResults(Run<?, ?> build, HPRunnerType hpRunnerType, List<FilePath> reports, boolean stripPackageAndClass, String jenkinsRootUrl) throws IOException, InterruptedException {
			this.reports = reports;
			this.filePath = new FilePath(build.getRootDir()).createTempFile(TEMP_TEST_RESULTS_FILE_NAME_PREFIX, null);
			this.buildStarted = build.getStartTimeInMillis();
			this.workspace = BuildHandlerUtils.getWorkspace(build);
			this.stripPackageAndClass = stripPackageAndClass;
			this.hpRunnerType = hpRunnerType;
			this.jenkinsRootUrl = jenkinsRootUrl;
			String buildRootDir = build.getRootDir().getCanonicalPath();
			this.sharedCheckOutDirectory = CheckOutSubDirEnvContributor.getSharedCheckOutDirectory(build.getParent());
			if (sharedCheckOutDirectory == null && (HPRunnerType.UFT.equals(hpRunnerType) || HPRunnerType.UFT_MBT.equals(hpRunnerType))) {
				ParametersAction parameterAction = build.getAction(ParametersAction.class);
				ParameterValue pv = parameterAction != null ? parameterAction.getParameter(UftConstants.UFT_CHECKOUT_FOLDER) : null;
				sharedCheckOutDirectory = pv != null && pv instanceof StringParameterValue ?
						StringUtils.strip((String) pv.getValue(), "\\/") : "";
			}

			this.jobName = JobProcessorFactory.getFlowProcessor(build.getParent()).getTranslatedJobName();
			this.buildId = build.getId();
			moduleDetection = Arrays.asList(
					new MavenBuilderModuleDetection(build),
					new MavenSetModuleDetection(build),
					new ModuleDetection.Default());


			if (HPRunnerType.UFT.equals(hpRunnerType) || HPRunnerType.UFT_MBT.equals(hpRunnerType)) {
				Node node = JenkinsUtils.getCurrentNode(workspace);
				this.nodeName = node != null && !node.getNodeName().isEmpty() ? node.getNodeName() : "";
				//extract folder names for created tests
				String reportFolder = buildRootDir + "/archive/UFTReport" +
						(StringUtils.isNotEmpty(this.nodeName) ? "/" + this.nodeName : "");
				List<String> testFolderNames = new ArrayList<>();
				testFolderNames.add(build.getRootDir().getAbsolutePath());
				File reportFolderFile = new File(reportFolder);
				if (reportFolderFile.exists()) {
					File[] children = reportFolderFile.listFiles();
					if (children != null) {
						for (File child : children) {
							testFolderNames.add(child.getName());
						}
					}
				}
				additionalContext = testFolderNames;
			}
			if (HPRunnerType.StormRunnerLoad.equals(hpRunnerType)) {
				try {
					File file = new File(build.getRootDir(), "log");
					Path path = Paths.get(file.getPath());
					additionalContext = Files.readAllLines(path, StandardCharsets.UTF_8);
				} catch (Exception e) {
					logger.error("Failed to add log file for StormRunnerLoad :" + e.getMessage());
				}
			}
			if(build.getAction(ParametersAction.class) != null && build.getAction(ParametersAction.class).getParameter(TEST_RESULT_NAME_REGEX_PATTERN_PARAMETER_NAME) != null &&
					build.getAction(ParametersAction.class).getParameter(TEST_RESULT_NAME_REGEX_PATTERN_PARAMETER_NAME).getValue() != null) {
				try {
					//\[.*\] - input for testName = testName[parameters]
					//\[.*\) - input for testName = testName[parameters](more params)
					this.testParserRegEx = Pattern.compile(Objects.requireNonNull(build.getAction(ParametersAction.class).getParameter(TEST_RESULT_NAME_REGEX_PATTERN_PARAMETER_NAME).getValue()).toString());
				} catch (IllegalArgumentException e){
					logger.error("Failed to parse regular expression pattern for test result name extractor.Job name: {}, Build {}, Input: {}, Error massage: {}.",
							this.jobName,
							this.buildId,
							Objects.requireNonNull(build.getAction(ParametersAction.class).getParameter(TEST_RESULT_NAME_REGEX_PATTERN_PARAMETER_NAME).getValue()).toString() +"\n",
							e.getMessage());
				}
			}
			if (hpRunnerType.equals(HPRunnerType.UFT_MBT)) {
				try {
					ParametersAction parameterAction = build.getAction(ParametersAction.class);
					ParameterValue pv = parameterAction != null ? parameterAction.getParameter(OCTANE_CONFIG_ID_PARAMETER_NAME) : null;
					String instanceId = pv instanceof StringParameterValue ? StringUtils.strip((String) pv.getValue(), "\\/") : "";
					OctaneClient octaneClient = OctaneSDK.getClientByInstanceId(instanceId);
					octaneSupportsSteps = octaneClient.getConfigurationService().isOctaneVersionGreaterOrEqual("16.0.217");
				} catch (Exception e) {
					logger.error("Failed to check octane version to know whether octaneSupportsSteps", e);
				}
			}
		}

		@Override
		public FilePath invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
			OutputStream os = filePath.write();
			BufferedOutputStream bos = new BufferedOutputStream(os);
			ObjectOutputStream oos = new ObjectOutputStream(bos);

			try {
				for (FilePath report : reports) {
					JUnitXmlIterator iterator = new JUnitXmlIterator(report.read(), moduleDetection, workspace, sharedCheckOutDirectory, jobName, buildId, buildStarted, stripPackageAndClass, hpRunnerType, jenkinsRootUrl, additionalContext,testParserRegEx, octaneSupportsSteps,nodeName);
					while (iterator.hasNext()) {
						oos.writeObject(iterator.next());
					}
				}
			} catch (XMLStreamException e) {
				throw new IOException(e);
			}
			os.flush();

			oos.close();
			return filePath;
		}

		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {
			roleChecker.check(this, Role.UNKNOWN);
		}
	}

	/*
	 * To be used in tests only.
	 */
	public void _setResultFieldsDetectionService(ResultFieldsDetectionService detectionService) {
		this.resultFieldsDetectionService = detectionService;
	}
}
