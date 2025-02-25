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

package com.microfocus.application.automation.tools.results.parser.nunit3;

import com.microfocus.application.automation.tools.results.parser.ReportParseException;
import com.microfocus.application.automation.tools.results.parser.ReportParser;
import com.microfocus.application.automation.tools.results.parser.antjunit.AntJUnitReportParserImpl;
import com.microfocus.application.automation.tools.results.service.almentities.AlmTestSet;
import hudson.FilePath;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * NUnit3 Report Parser implement.
 * It will convert Nunit 3 report to Junit report with xsl then start AntJunit parser.
 */
public class NUnit3ReportParserImpl implements ReportParser {

    private static final String TEMP_JUNIT_FILE_PREFIX = "temp-junit";
    private static final String TEMP_JUNIT_FILE_SUFFIX = ".xml";
    private static final String NUNIT_TO_JUNIT_XSLFILE = "nunit-to-junit.xsl";

    private FilePath workspace;

    public NUnit3ReportParserImpl(FilePath workspace) {
        this.workspace = workspace;
    }

    @Override
    public List<AlmTestSet> parseTestSets(InputStream reportInputStream, String testingFramework, String testingTool)
            throws ReportParseException {

        // Use the xsl to convert the nunit3 and nunit to junit and then parse with junit logic.
        // This can be extended to cover all kinds of result format.
        // When new format comes, only need to provide a xsl, no need to change any code.

        FileOutputStream fileOutputStream = null;
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer nunitTransformer = transformerFactory.newTransformer(
                        new StreamSource(this.getClass().getResourceAsStream(NUNIT_TO_JUNIT_XSLFILE)));
            File junitTargetFile = new File(workspace.createTempFile(TEMP_JUNIT_FILE_PREFIX, TEMP_JUNIT_FILE_SUFFIX).toURI());
            fileOutputStream = new FileOutputStream(junitTargetFile);
            nunitTransformer.transform(new StreamSource(reportInputStream), new StreamResult(fileOutputStream));

            InputStream in = new FileInputStream(junitTargetFile);
            return new AntJUnitReportParserImpl().parseTestSets(in, testingFramework, testingTool);

        } catch (Exception e) {
            throw new ReportParseException(e);

        } finally {
            try {
                if (reportInputStream != null) {
                    reportInputStream.close();
                }
            } catch (IOException e) {
                throw new ReportParseException(e);
            }

            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                throw new ReportParseException(e);
            }
        }
    }
}
