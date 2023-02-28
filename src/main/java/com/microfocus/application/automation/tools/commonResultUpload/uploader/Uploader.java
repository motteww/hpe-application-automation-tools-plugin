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
package com.microfocus.application.automation.tools.commonResultUpload.uploader;

import com.microfocus.application.automation.tools.commonResultUpload.CommonUploadLogger;
import com.microfocus.application.automation.tools.commonResultUpload.service.CustomizationService;
import com.microfocus.application.automation.tools.commonResultUpload.service.FolderService;
import com.microfocus.application.automation.tools.commonResultUpload.service.RestService;
import com.microfocus.application.automation.tools.commonResultUpload.service.UDFTranslator;
import com.microfocus.application.automation.tools.commonResultUpload.service.VersionControlService;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.configloader.RunStatusMapLoader;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.model.EntitiesFieldMap;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.configloader.EntitiesFieldMapLoader;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.model.RunStatusMap;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.XmlReader;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.model.XmlResultEntity;
import com.microfocus.application.automation.tools.rest.RestClient;
import com.microfocus.application.automation.tools.results.service.AlmRestTool;
import com.microfocus.application.automation.tools.results.service.AttachmentUploadService;
import com.microfocus.application.automation.tools.sse.sdk.authenticator.AuthenticationTool;
import hudson.FilePath;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.microfocus.application.automation.tools.commonResultUpload.ParamConstant.*;

public class Uploader {

    private RestClient restClient;
    private Map<String, String> params;
    private CommonUploadLogger logger;
    private CustomizationService cs;
    private VersionControlService vs;
    private UDFTranslator udt;
    private RestService rs;
    private FolderService fs;
    private Run<?, ?> run;
    private FilePath workspace;

    public Uploader(Run<?, ?> run, FilePath workspace, CommonUploadLogger logger, Map<String, String> params) {
        this.run = run;
        this.workspace = workspace;
        this.logger = logger;
        this.params = params;
    }

    public void upload() {
        restClient = new RestClient(params.get(ALM_SERVER_URL),
                params.get(ALM_DOMAIN),
                params.get(ALM_PROJECT),
                params.get(USERNAME));

        boolean login = AuthenticationTool.getInstance().authenticate(restClient,
                params.get(USERNAME), params.get(PASS),
                params.get(ALM_SERVER_URL), params.get(CLIENT_TYPE), logger);
        if (login) {
            init();
            if (!rs.getDomains().contains(params.get(ALM_DOMAIN))) {
                logger.error("Invalid domain name:" + params.get(ALM_DOMAIN));
                return;
            }
            if (!rs.getProjects(params.get(ALM_DOMAIN)).contains(params.get(ALM_PROJECT))) {
                logger.error("Invalid project name:" + params.get(ALM_PROJECT));
                return;
            }
            List<XmlResultEntity> xmlResultEntities = getUploadData();
            if (xmlResultEntities == null || xmlResultEntities.size() == 0) {
                return;
            }
            TestSetUploader testSetUploader = getTestSetUploader();
            if (testSetUploader == null) {
                return;
            }
            AlmRestTool almRestTool = new AlmRestTool(restClient, logger);
            params.put(ACTUAL_USER, almRestTool.getActualUsername());
            testSetUploader.upload(xmlResultEntities);
        } else {
            logger.error("Login failed.");
        }
    }

    private void init() {
        cs = new CustomizationService(restClient, logger);
        vs = new VersionControlService(restClient, logger);
        udt = new UDFTranslator(cs, logger);
        rs = new RestService(restClient, logger, udt);
        fs = new FolderService(rs);
        AttachmentUploadService.init(run, workspace, restClient, logger);
    }

    private TestSetUploader getTestSetUploader() {
        RunStatusMap runStatusMap = RunStatusMapLoader.load(params.get(RUN_STATUS_MAPPING), logger);
        if (runStatusMap == null) {
            return null;
        }
        RunUploader runu = new RunUploader(logger, params, rs, cs, runStatusMap.getStatus());
        TestInstanceUploader tiu = new TestInstanceUploader(logger, params, rs, runu, cs);
        TestUploader testu = new TestUploader(logger, params, rs, fs, tiu, cs, vs);
        return new TestSetUploader(logger, params, rs, fs, testu);
    }

    private List<XmlResultEntity> getUploadData() {
        List<XmlResultEntity> xmlResultEntities = new ArrayList<>();

        EntitiesFieldMap entitiesFieldMap = EntitiesFieldMapLoader.load(params.get(FIELD_MAPPING), logger, cs,
                "true".equals(params.get(CREATE_NEW_TEST)));
        if (entitiesFieldMap == null) {
            return xmlResultEntities;
        }

        XmlReader xmlReader = new XmlReader(run, workspace, logger);
        xmlResultEntities = xmlReader.scan(params.get(TESTING_RESULT_FILE), entitiesFieldMap);
        if (xmlResultEntities == null || xmlResultEntities.size() == 0) {
            logger.error("No test result content is found.");
        }
        return xmlResultEntities;
    }
}
