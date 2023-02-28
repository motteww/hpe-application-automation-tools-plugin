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

package com.microfocus.application.automation.tools.octane.tests.xml;

import com.hp.octane.integrations.testresults.XmlWritableTestResult;
import com.microfocus.application.automation.tools.octane.tests.TestResultContainer;
import com.microfocus.application.automation.tools.octane.tests.build.BuildDescriptor;
import com.microfocus.application.automation.tools.octane.tests.build.BuildHandlerUtils;
import com.microfocus.application.automation.tools.octane.tests.detection.ResultFields;
import hudson.FilePath;
import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Save results to mqmTests.xml in XML format
 */
@SuppressWarnings("all")
public class TestResultXmlWriter {

	private FilePath targetPath;
	private BuildDescriptor buildDescriptor;

	private XMLStreamWriter writer;
	private OutputStream outputStream;

	public TestResultXmlWriter(FilePath targetPath, BuildDescriptor buildDescriptor) {
		this.targetPath = targetPath;
		this.buildDescriptor = buildDescriptor;
	}

	public TestResultXmlWriter(FilePath targetPath, Run build) {
		this.targetPath = targetPath;
		this.buildDescriptor = BuildHandlerUtils.getBuildType(build);
	}

	public void writeResults(TestResultContainer testResultContainer) throws InterruptedException, XMLStreamException, IOException {
		if (testResultContainer != null) {
			ResultFields resultFields = testResultContainer.getResultFields();
			initialize(resultFields);

			Iterator<XmlWritableTestResult> testResults = testResultContainer.getIterator();
			while (testResults.hasNext()) {
				XmlWritableTestResult testResult = testResults.next();
				testResult.writeXmlElement(writer);
			}
		}
	}

	public void close() throws XMLStreamException {
		if (outputStream != null) {
			writer.writeEndElement(); // test_runs
			writer.writeEndElement(); // test_result
			writer.writeEndDocument();
			writer.close();
			IOUtils.closeQuietly(outputStream);
		}
	}

	private void initialize(ResultFields resultFields) throws IOException, InterruptedException, XMLStreamException {
		if (outputStream == null) {
			outputStream = targetPath.write();
			writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name() );
			writer.writeStartDocument();

			writer.writeStartElement("test_result");
			writer.writeStartElement("build");
			writer.writeAttribute("server_id", "to-be-filled-in-SDK");
			writer.writeAttribute("job_id", buildDescriptor.getJobId());
			writer.writeAttribute("build_id", buildDescriptor.getBuildId());
			if (!StringUtils.isEmpty(buildDescriptor.getSubType())) {
				writer.writeAttribute("sub_type", buildDescriptor.getSubType());
			}
			writer.writeEndElement(); // build
			writeFields(resultFields);
			writer.writeStartElement("test_runs");
		}
	}

	private void writeFields(ResultFields resultFields) throws XMLStreamException {
		if (resultFields != null) {
			writer.writeStartElement("test_fields");
			writeField("Framework", resultFields.getFramework());
			writeField("Test_Level", resultFields.getTestLevel());
			writeField("Testing_Tool_Type", resultFields.getTestingTool());
			writeField("Test_Type", resultFields.getTestType());
			writer.writeEndElement();
		}
	}

	private void writeField(String type, String value) throws XMLStreamException {
		if (StringUtils.isNotEmpty(value)) {
			writer.writeStartElement("test_field");
			writer.writeAttribute("type", type);
			writer.writeAttribute("value", value);
			writer.writeEndElement();
		}
	}
}
