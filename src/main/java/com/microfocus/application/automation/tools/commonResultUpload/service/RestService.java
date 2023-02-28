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

package com.microfocus.application.automation.tools.commonResultUpload.service;

import com.microfocus.application.automation.tools.common.SSEException;
import com.microfocus.application.automation.tools.commonResultUpload.CommonUploadLogger;
import com.microfocus.application.automation.tools.commonResultUpload.rest.CreateAlmEntityEntityRequest;
import com.microfocus.application.automation.tools.commonResultUpload.rest.GetAlmEntityRequest;
import com.microfocus.application.automation.tools.commonResultUpload.rest.UpdateAlmEntityEntityRequest;
import com.microfocus.application.automation.tools.rest.RestClient;
import com.microfocus.application.automation.tools.sse.common.XPathUtils;
import com.microfocus.application.automation.tools.sse.sdk.ResourceAccessLevel;
import com.microfocus.application.automation.tools.sse.sdk.Response;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestService {

    private RestClient restClient;
    private CreateAlmEntityEntityRequest createAlmEntityRequest;
    private GetAlmEntityRequest getAlmEntityRequest;
    private UpdateAlmEntityEntityRequest updateAlmEntityRequest;
    private UDFTranslator udt;
    private CommonUploadLogger logger;

    public RestService(RestClient restClient, CommonUploadLogger logger, UDFTranslator udt) {
        this.restClient = restClient;
        this.logger = logger;
        createAlmEntityRequest = new CreateAlmEntityEntityRequest(restClient, logger);
        getAlmEntityRequest = new GetAlmEntityRequest(restClient, logger);
        updateAlmEntityRequest = new UpdateAlmEntityEntityRequest(restClient, logger);
        this.udt = udt;
    }

    public Map<String, String> create(String restPrefix, Map<String, String> valueMap) {
        udt.translate(restPrefix, valueMap);
        return createAlmEntityRequest.perform(restPrefix, valueMap);
    }

    public List<Map<String, String>> get(String id, String restPrefix, String queryString) {
        return getAlmEntityRequest.perform(id, restPrefix, queryString);
    }

    public Map<String, String> update(String restPrefix, Map<String, String> valueMap) {
        udt.translate(restPrefix, valueMap);
        return updateAlmEntityRequest.perform(restPrefix, valueMap);
    }

    public List<String> getDomains() {
        String url = restClient.getServerUrl();
        if (!url.endsWith("/")) {
            url = String.format("%s/", url);
        }
        url = String.format("%s%s/domains", url, "rest");
        return getDomainOrProjectListFromResponse("Domain", url);
    }

    public List<String> getProjects(String domain) {
        String url = restClient.getServerUrl();
        if (!url.endsWith("/")) {
            url = String.format("%s/", url);
        }
        url = String.format("%s%s/domains/%s/projects", url, "rest", domain);
        return getDomainOrProjectListFromResponse("Project", url);
    }

    private List<String> getDomainOrProjectListFromResponse(String dopo, String url) {
        Response response = restClient.httpGet(url, null, null,
                ResourceAccessLevel.PROTECTED);

        List<String> list = new ArrayList<>();
        if (response.isOk() && !response.toString().equals("")) {
            Document document = null;
            try {
                document = XPathUtils.getDocument(response.toString());
            } catch (SSEException e) {
                logger.error("Get xml document failed: " + e.getMessage());
                logger.error("Please check ALM server's status.");
            }
            if (document != null) {
                NodeList domainList = document.getElementsByTagName(dopo);
                for (int i = 0; i < domainList.getLength(); i++) {
                    String project = domainList.item(i).getAttributes().getNamedItem("Name").getTextContent();
                    list.add(project);
                }
            } else {
                logger.error("Cannot get any content from response while getting " + dopo);
            }
        } else {
            logger.error("Get " + dopo + "s failed from: " + url);
            logger.error(response.getFailure().toString());
        }
        return list;
    }
}
