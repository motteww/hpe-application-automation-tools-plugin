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

package com.microfocus.application.automation.tools.commonResultUpload;

import com.microfocus.application.automation.tools.sse.sdk.Logger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class CommonUploadLogger implements Logger {

    private static final String ERR_PREFIX = "ERR: ";
    private static final String INFO_PREFIX = "INFO: ";
    private static final String WARN_PREFIX = "WARN: ";

    private List<String> failedMessages;
    private PrintStream printStream;

    public CommonUploadLogger(PrintStream printStream) {
        this.printStream = printStream;
        failedMessages = new ArrayList<>();
    }

    public void error(String message) {
        failedMessages.add(message);
        message = ERR_PREFIX + message;
        log(message);
    }

    public void info(String message) {
        message = INFO_PREFIX + message;
        log(message);
    }

    public void warn(String message) {
        message = WARN_PREFIX + message;
        log(message);
    }

    @Override
    public void log(String message) {
        if (printStream != null) {
            printStream.println(message);
        }
    }

    public List<String> getFailedMessages() {
        return failedMessages;
    }

}
