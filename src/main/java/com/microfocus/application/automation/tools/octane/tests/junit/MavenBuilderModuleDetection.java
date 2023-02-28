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

package com.microfocus.application.automation.tools.octane.tests.junit;

import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Run;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.File;

public class MavenBuilderModuleDetection extends AbstractMavenModuleDetection {

    public MavenBuilderModuleDetection(Run build) {
        super(build);
    }

    protected void addPomDirectories(Run build) {
        if (build instanceof AbstractBuild) {

            if (((AbstractBuild)build).getProject() instanceof FreeStyleProject ||
                    JobProcessorFactory.MATRIX_CONFIGURATION_NAME.equals(((AbstractBuild)build).getProject().getClass().getName())) {
                boolean unknownBuilder = false;
                for (Builder builder : ((Project<?, ?>) ((AbstractBuild)build).getProject()).getBuilders()) {
                    if (builder instanceof Maven) {
                        Maven maven = (Maven) builder;
                        if (maven.pom != null) {
                            if (maven.pom.endsWith("/pom.xml") || maven.pom.endsWith("\\pom.xml")) {
                                addPomDirectory(new FilePath(rootDir, maven.pom.substring(0, maven.pom.length() - 8)));
                                continue;
                            } else {
                                int p = maven.pom.lastIndexOf(File.separatorChar);
                                if (p > 0) {
                                    addPomDirectory(new FilePath(rootDir, maven.pom.substring(0, p)));
                                    continue;
                                }
                            }
                        }
                        addPomDirectory(rootDir);
                    } else {
                        unknownBuilder = true;
                    }
                }
                if (unknownBuilder && rootDir != null && !pomDirs.contains(rootDir)) {
                    // attempt to support shell and batch executions too
                    // simply assume there is top-level pom file for any non-maven builder
                    addPomDirectory(rootDir);
                }
            }
        } else if (JobProcessorFactory.WORKFLOW_RUN_NAME.equals(WorkflowRun.class.getName()) && rootDir != null) {
            addPomDirectory(rootDir);
        }
    }
}
