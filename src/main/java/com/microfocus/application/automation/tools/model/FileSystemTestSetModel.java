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

package com.microfocus.application.automation.tools.model;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Properties;

/**
 * Represents the list of tests and environments which ParallelRunner will execute.
 */
public class FileSystemTestSetModel extends AbstractDescribableImpl<FileSystemTestSetModel> {
    private List<FileSystemTestModel> fileSystemTestSet;

    /**
     * Construct a new FileSystemTestSetModel.
     * @param fileSystemTestSet the list of tests and environments
     */
    @DataBoundConstructor
    public FileSystemTestSetModel(List<FileSystemTestModel> fileSystemTestSet) {
        this.fileSystemTestSet = fileSystemTestSet;
    }

    /**
     * Returns the list of tests and environments
     * @return the list of tests and environments
     */
    public List<FileSystemTestModel> getFileSystemTestSet() {
        return fileSystemTestSet;
    }

    /**
     * Remove the fsTests as ParallelRunner won't run those.
     * @param props the properties
     */
    private void removeFsTests(Properties props) {
        int current = 1;
        String key = "Test";

        while(props.containsKey(key + current)) {
            props.remove(key + current);
            current+=1;
        }
    }

    /**
     * Add the parallel runner tests and environments to the properties.
     * @param props the properties
     * @param envVars the environment variables
     */
    public void addTestSetProperties(Properties props,EnvVars envVars) {
        // enabled parallel runner for build
        props.put("parallelRunnerMode","true");

        // remove the tests set by fs
        removeFsTests(props);

        int testNumber = 1;

        for(FileSystemTestModel testModel : this.fileSystemTestSet) {
            List<String> tests = testModel.parseTests(envVars);

            //  these are the environments for each test
            // from the tests list
            List<ParallelRunnerEnvironmentModel> environmentModels = testModel.getParallelRunnerEnvironments();

            for(String test: tests) {
                test = test.trim();

                // first add the test
                props.put("Test" + testNumber,test);

                int envNumber = 1;

                // each parallel test environment will be of the form
                // ParallelTest1Env1, ParallelTest1Env2,..., ParallelTest1EnvN
                for(ParallelRunnerEnvironmentModel environment : environmentModels) {
                    // add the environment for each test
                    props.put("ParallelTest" + testNumber+ "Env" + envNumber,environment.getEnvironment());
                    envNumber++;
                }

                // increment the number of available tests
                testNumber++;
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<FileSystemTestSetModel> {
        @Nonnull
        public String getDisplayName() {return "File System test set model";}
    }
}
