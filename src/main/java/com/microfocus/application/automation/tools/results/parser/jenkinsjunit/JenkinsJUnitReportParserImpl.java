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

package com.microfocus.application.automation.tools.results.parser.jenkinsjunit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.microfocus.application.automation.tools.results.parser.ReportParseException;
import com.microfocus.application.automation.tools.results.parser.ReportParser;
import com.microfocus.application.automation.tools.results.parser.util.ParserUtil;
import com.microfocus.application.automation.tools.results.service.almentities.AlmRun;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTest;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTestInstance;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTestInstanceImpl;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTestSet;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTestSetImpl;
import com.microfocus.application.automation.tools.results.service.almentities.EntityRelation;
import com.microfocus.application.automation.tools.results.service.almentities.IAlmConsts;
import com.microfocus.application.automation.tools.sse.sdk.Base64Encoder;

public class JenkinsJUnitReportParserImpl implements ReportParser {

	public List<AlmTestSet> parseTestSets(InputStream reportInputStream,
                                          String testingFramework, String testingTool) throws ReportParseException {
		
		try {
			return parseTestSetsFromJenkinsPluginJUnitReport(reportInputStream, testingFramework, testingTool);
		} catch (Exception e) {
		
			//e.printStackTrace();
			throw new ReportParseException();
		}
	}	
	
	private Result parseFromJenkinsPluginJUnitReport(InputStream reportInputStream) throws JAXBException {
		JAXBContext jaxbContext;
		Thread t = Thread.currentThread();
		ClassLoader orig = t.getContextClassLoader();
		t.setContextClassLoader(JenkinsJUnitReportParserImpl.class.getClassLoader());
		try {
			jaxbContext = JAXBContext.newInstance(Result.class);
		} finally {
			t.setContextClassLoader(orig);
		}
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		return (Result)unmarshaller.unmarshal(reportInputStream);		
	}
	
	private AlmTest createExternalTestForJenkinsPluginJUnit(Result.Suites.Suite.Cases.Case c, String testingFramework, String testingTool) {
	
		return ParserUtil.createExternalTest(c.getClassName(), c.getTestName(), testingFramework, testingTool);
	}
	
	private String getRunDetail(Result.Suites.Suite.Cases.Case c){
		String detail = ParserUtil.marshallerObject(Result.Suites.Suite.Cases.Case.class, c);
		return Base64Encoder.encode(detail.getBytes());
	}

	private ArrayList<AlmTestSet> parseTestSetsFromJenkinsPluginJUnitReport(InputStream reportInputStream, String testingFramework, String testingTool) throws JAXBException {
		Result result = parseFromJenkinsPluginJUnitReport(reportInputStream);		
		ArrayList<AlmTestSet> testSets = new ArrayList<AlmTestSet>();
		
		for (Result.Suites suites : result.getSuites()) {
			for (Result.Suites.Suite suite : suites.getSuite()) {
				AlmTestSet testSet = new AlmTestSetImpl();
				testSet.setFieldValue(AlmTestSet.TESTSET_NAME, ParserUtil.replaceInvalidCharsForTestSetName(suite.getName()));
				testSet.setFieldValue(AlmTestSet.TESTSET_SUB_TYPE_ID, EXTERNAL_TEST_SET_TYPE_ID);
				testSets.add(testSet);
				
				for (Result.Suites.Suite.Cases cases : suite.getCases()) {
					for (Result.Suites.Suite.Cases.Case c : cases.getCase()){
						AlmTestInstance testInstance = new AlmTestInstanceImpl();
						testInstance.setFieldValue(AlmTestInstance.TEST_INSTANCE_SUBTYPE_ID, EXTERNAL_TEST_INSTANCE_TYPE_ID);
						testSet.addRelatedEntity(EntityRelation.TESTSET_TO_TESTINSTANCE_CONTAINMENT_RELATION, testInstance);
						
						AlmTest test = createExternalTestForJenkinsPluginJUnit(c, testingFramework, testingTool);
						testInstance.addRelatedEntity(EntityRelation.TEST_TO_TESTINSTANCE_REALIZATION_RELATION, test);
						
						AlmRun run = ParserUtil.createRun(getRunStatus(c),
														suite.getTimestamp(),  
														c.getDuration(), 
														getRunDetail (c));
						testInstance.addRelatedEntity(EntityRelation.TESTINSTANCE_TO_RUN_REALIZATION_RELATION, run);
					}
				}
			}
		}
		
		return testSets;
	}
	
	private String getRunStatus(Result.Suites.Suite.Cases.Case c) {
		String result;
		if (c.getSkipped() != null && c.getSkipped().equals("true")) {
			result = IAlmConsts.IStatuses.NO_RUN.value();

		} else if (c.getErrorStackTrace() != null && c.getErrorStackTrace().length() > 0) {
			result = IAlmConsts.IStatuses.FAILED.value();

		} else if (c.getErrorDetails() != null && c.getErrorDetails().length() > 0) {
			result = IAlmConsts.IStatuses.FAILED.value();

		} else if (c.getFailedSince() != null && c.getFailedSince().equals("0")) {
			result = IAlmConsts.IStatuses.PASSED.value();

		} else {
			result = IAlmConsts.IStatuses.FAILED.value();
		}
		return result;
	}
}
