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

package com.microfocus.application.automation.tools.octane.model;

import com.microfocus.application.automation.tools.sse.common.StringUtils;
import hudson.EnvVars;
import hudson.maven.MavenModuleSet;
import hudson.model.*;
import hudson.plugins.sonar.SonarGlobalConfiguration;
import hudson.plugins.sonar.SonarInstallation;
import hudson.plugins.sonar.SonarRunnerBuilder;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * the adapter enables usage of sonar classes and interfaces without adding a full dependency on
 * sonar plugin.
 * this class is only dependent on sonar plugin for compile time.
 */

public class SonarHelper {

    public enum DataType {VULNERABILITIES, COVERAGE}

    public static final String SONAR_GLOBAL_CONFIG = "hudson.plugins.sonar.SonarGlobalConfiguration";
    private static final String SONAR_ACTION_ID = "hudson.plugins.sonar.SonarRunnerBuilder";
    private static final String SONAR_SERVER_HOST_VARIABLE = "SONAR_HOST_URL";
    private static final String SONAR_SERVER_TOKEN_VARIABLE = "SONAR_AUTH_TOKEN";

    private String serverUrl;
    private String serverToken;

    public SonarHelper(Run<?, ?> run, TaskListener listener) {
        DescribableList<Builder, Descriptor<Builder>> postbuilders = null;
        if (run instanceof AbstractBuild) {
            AbstractBuild abstractBuild = (AbstractBuild) run;
            setSonarDetailsFromMavenEnvironment(abstractBuild, listener);
            AbstractProject project = abstractBuild.getProject();

            // for jobs that does not use new sonar approach for maven jobs,
            // extract data for old approach which used post build step
            if (StringUtils.isNullOrEmpty(this.getServerUrl()) || StringUtils.isNullOrEmpty(this.getServerToken())) {
                if (project instanceof MavenModuleSet) {
                    postbuilders = ((MavenModuleSet) project).getPostbuilders();
                } else if (project instanceof Project) {
                    postbuilders = ((Project) project).getBuildersList();
                }
                if (postbuilders != null){
                    setDataFromSonarBuilder(postbuilders, run);
                }
            }
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getServerToken() {
        return serverToken;
    }

    // used by the web hook
    public static String getSonarInstallationTokenByUrl(GlobalConfiguration sonarConfiguration, String sonarUrl, Run run) {
        if (sonarConfiguration instanceof SonarGlobalConfiguration) {
            SonarGlobalConfiguration sonar = (SonarGlobalConfiguration) sonarConfiguration;
            Optional<SonarInstallation> installation = Arrays.stream(sonar.getInstallations())
                    .filter(sonarInstallation -> sonarInstallation.getServerUrl().equals(sonarUrl))
                    .findFirst();
            if (installation.isPresent()) {
                return extractAuthenticationToken(installation.get(), run);
            }
        }
        return "";
    }

    private static String extractAuthenticationToken(SonarInstallation sonarInstallation, Run run) {
        return sonarInstallation.getServerAuthenticationToken(run);
    }

    private void setSonarDetailsFromMavenEnvironment(AbstractBuild build, TaskListener listener) {
        EnvVars environment;
        try {
            environment = build.getEnvironment(listener);
            if (environment != null) {
                this.serverUrl = environment.get(SONAR_SERVER_HOST_VARIABLE, "");
                this.serverToken = environment.get(SONAR_SERVER_TOKEN_VARIABLE, "");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setDataFromSonarBuilder(DescribableList<Builder, Descriptor<Builder>> postBuilders, Run run) {
        Builder sonarBuilder = postBuilders.getDynamic(SONAR_ACTION_ID);
        if (sonarBuilder != null) {
            SonarRunnerBuilder builder = (SonarRunnerBuilder) sonarBuilder;
            this.serverUrl = extractSonarUrl(builder);
            this.serverToken = extractSonarToken(builder, run);
        }
    }

    /**
     * get sonar URL address
     *
     * @return Sonar's URL
     */
    private String extractSonarUrl(SonarRunnerBuilder builder) {
        return builder != null ? builder.getSonarInstallation().getServerUrl() : "";
    }

    /**
     * get sonar server token
     *
     * @return Sonar's auth token
     */
    private String extractSonarToken(SonarRunnerBuilder builder, Run run) {
        String result  = builder != null  ? extractAuthenticationToken(builder.getSonarInstallation(), run) : "";
        return result;
    }
}
