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

package com.microfocus.application.automation.tools.octane.tests;

import com.microfocus.application.automation.tools.octane.tests.junit.JUnitTestResult;
import com.microfocus.application.automation.tools.octane.tests.junit.TestResultStatus;
import hudson.util.IOUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class TestResultIterator implements Iterator<JUnitTestResult> {

	private Reader input;
	private XMLEventReader reader;
	private LinkedList<JUnitTestResult> items = new LinkedList<>();
	private boolean closed;
	private String serverId;
	private String jobId;
	private String buildId;
	private String subType;

	public TestResultIterator(Reader input) throws XMLStreamException {
		this.input = input;
		reader = XMLInputFactory.newInstance().createXMLEventReader(input);
	}

	@Override
	public boolean hasNext() {
		try {
			while (items.isEmpty() && !closed) {
				if (reader.hasNext()) {
					XMLEvent event = reader.nextEvent();
					if (event instanceof StartElement) {
						Attribute attribute;
						StartElement element = (StartElement) event;
						String localName = element.getName().getLocalPart();
						if ("test_run".equals(localName)) {
							String moduleName = element.getAttributeByName(new QName("module")).getValue();
							String packageName = element.getAttributeByName(new QName("package")).getValue();
							String className = element.getAttributeByName(new QName("class")).getValue();
							String testName = element.getAttributeByName(new QName("name")).getValue();
							long duration = Long.valueOf(element.getAttributeByName(new QName("duration")).getValue());
							TestResultStatus status = TestResultStatus.fromPrettyName(element.getAttributeByName(new QName("status")).getValue());
							long started = Long.valueOf(element.getAttributeByName(new QName("started")).getValue());
							items.add(new JUnitTestResult(moduleName, packageName, className, testName, status, duration, started, null, null, null,null,null, null, false));
						} else if ("build".equals(localName)) {
							attribute = element.getAttributeByName(new QName("server_id"));
							if (attribute != null) {
								serverId = attribute.getValue();
							}
							attribute = element.getAttributeByName(new QName("job_id"));
							if (attribute != null) {
								jobId = attribute.getValue();
							}
							attribute = element.getAttributeByName(new QName("build_id"));
							if (attribute != null) {
								buildId = attribute.getValue();
							}
							attribute = element.getAttributeByName(new QName("sub_type"));
							if (attribute != null) {
								this.subType = attribute.getValue();
							}
						}
					}
				} else {
					closed = true;
					IOUtils.closeQuietly(input);
					reader.close();
				}
			}
			return !items.isEmpty();
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public JUnitTestResult next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		return items.removeFirst();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	public String getServerId() {
		hasNext();
		return serverId;
	}

	public String getJobId() {
		hasNext();
		return jobId;
	}

	public String getBuildId() {
		hasNext();
		return buildId;
	}

	public String getSubType() {
		hasNext();
		return subType;
	}
}
