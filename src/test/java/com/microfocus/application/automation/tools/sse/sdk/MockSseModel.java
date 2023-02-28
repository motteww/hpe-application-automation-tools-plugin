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

package com.microfocus.application.automation.tools.sse.sdk;

import com.microfocus.application.automation.tools.model.CdaDetails;
import com.microfocus.application.automation.tools.model.ProxySettings;
import com.microfocus.application.automation.tools.model.SecretContainer;
import com.microfocus.application.automation.tools.model.SecretContainerTest;
import com.microfocus.application.automation.tools.model.SseModel;

public class MockSseModel extends SseModel {
    
    public MockSseModel(
            String almServerName,
            String almUserName,
            String almPassword,
            String almDomain,
            String almProject,
            String runType,
            String almEntityId,
            String timeslotDuration,
            String description,
            String postRunAction,
            String environmentConfigurationId,
            CdaDetails cdaDetails,
            ProxySettings proxySettings) {
        
        super(
                almServerName,
                almUserName,
                almPassword,
                almDomain,
                null,
                almProject,
                runType,
                almEntityId,
                timeslotDuration,
                description,
                postRunAction,
                environmentConfigurationId,
                cdaDetails);
    }
    
    @Override
    protected SecretContainer setPassword(String almPassword) {
        
        SecretContainer secretContainer = new SecretContainerTest();
        secretContainer.initialize(almPassword);
        
        return secretContainer;
    }
}
