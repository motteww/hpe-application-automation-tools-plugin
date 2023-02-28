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

package com.microfocus.application.automation.tools.octane.tests.detection;

import com.microfocus.application.automation.tools.model.AlmServerSettingsModel;
import com.microfocus.application.automation.tools.uft.model.FilterTestsModel;
import com.microfocus.application.automation.tools.octane.tests.TestUtils;
import com.microfocus.application.automation.tools.octane.tests.detection.ResultFieldsXmlReader.TestAttributes;
import com.microfocus.application.automation.tools.octane.tests.detection.ResultFieldsXmlReader.TestResultContainer;
import com.microfocus.application.automation.tools.run.RunFromAlmBuilder;
import com.microfocus.application.automation.tools.run.RunFromFileBuilder;
import com.microfocus.application.automation.tools.uft.model.SpecifyParametersModel;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.scm.SubversionSCM;
import hudson.tasks.Maven;
import hudson.tasks.junit.JUnitResultArchiver;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("squid:S2699")
public class UFTExtensionTest {

	@ClassRule
	public static final JenkinsRule rule = new JenkinsRule();

	private ResultFieldsDetectionService detectionService;

	@Before
	public void before() {
		detectionService = new ResultFieldsDetectionService();
	}

	@Test
	public void testMockOneBuilder() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);
		project.getBuildersList().add(new RunFromFileBuilder("notExistingTest"));

		AbstractBuild buildMock = Mockito.mock(AbstractBuild.class);
		Mockito.when(buildMock.getProject()).thenReturn(project);

		ResultFields fields = detectionService.getDetectedFields(buildMock);
		assertUFTFields(fields);
	}

	@Test
	public void testMockMoreBuilders() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);
		FilterTestsModel filterTestsModel = new FilterTestsModel("testName", false, false, false, false, false);
		SpecifyParametersModel parametersModel = new SpecifyParametersModel("[]");
		AlmServerSettingsModel almServerSettingsModel =  new AlmServerSettingsModel("server2", "serverURL",  new ArrayList<>(), new ArrayList<>());
		project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" test -Dmaven.repo.local=%s\\m2-temp",
				TestUtils.getMavenHome(),System.getenv("TEMP")), ToolInstallations.configureMaven3().getName(), null, null, "-Dmaven.test.failure.ignore=true"));
		project.getBuildersList().add(new RunFromAlmBuilder("notExistingServer", "JOB", "sa", "", "domain", "project", "notExistingTests", "", "", "", "", "","", false, false, false, filterTestsModel, parametersModel, almServerSettingsModel));

		AbstractBuild buildMock = Mockito.mock(AbstractBuild.class);
		Mockito.when(buildMock.getProject()).thenReturn(project);

		ResultFields fields = detectionService.getDetectedFields(buildMock);
		assertUFTFields(fields);
	}

	@Test
	public void testFileBuilder() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);
		project.getBuildersList().add(new RunFromFileBuilder(""));

		//UFT plugin will not find any test -> that will cause failing the scheduled build
		//but as detection runs after completion of run, we are sure, that it did not fail because of detection service
		AbstractBuild build = project.scheduleBuild2(0).get();

		ResultFields fields = detectionService.getDetectedFields(build);
		assertUFTFields(fields);
	}

	@Ignore
	@Test
	public void testUFTEndToEnd() throws Exception {
		String projectName = "root-job-" + UUID.randomUUID().toString();
		FreeStyleProject project = rule.createFreeStyleProject(projectName);
		//TODO solve storing of example test
		SubversionSCM scm = new SubversionSCM("http://localhost:8083/svn/selenium/branches/uft");
		project.setScm(scm);
		project.getBuildersList().add(new RunFromFileBuilder("Calculator"));
		project.getPublishersList().add(new JUnitResultArchiver("Results*.xml"));
		//this will actually run the UFT test
		AbstractBuild build = TestUtils.runAndCheckBuild(project);

		File mqmTestsXml = new File(build.getRootDir(), "mqmTests.xml");
		ResultFieldsXmlReader xmlReader = new ResultFieldsXmlReader(new FileReader(mqmTestsXml));
		TestResultContainer container = xmlReader.readXml();
		assertUFTFields(container.getResultFields());
		assertUFTTestAttributes(container.getTestAttributes());
	}

	private void assertUFTFields(ResultFields fields) {
		Assert.assertNotNull(fields);
		Assert.assertEquals("UFT", fields.getFramework());
		Assert.assertEquals("UFT", fields.getTestingTool());
		Assert.assertNull(fields.getTestLevel());
	}

	private void assertUFTTestAttributes(List<TestAttributes> testAttributes) {
		for (TestAttributes test : testAttributes) {
			Assert.assertTrue(test.getModuleName().isEmpty());
			Assert.assertTrue(test.getPackageName().isEmpty());
			Assert.assertTrue(test.getClassName().isEmpty());
			Assert.assertTrue(!test.getTestName().isEmpty());
		}
	}
}
