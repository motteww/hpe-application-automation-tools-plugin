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

package com.microfocus.application.automation.tools.run;

import javax.annotation.Nonnull;
import java.io.PrintStream;

import com.microfocus.application.automation.tools.model.SvServerSettingsModel;
import com.microfocus.application.automation.tools.model.SvServiceSelectionModel;
import com.microfocus.application.automation.tools.model.SvUndeployModel;
import com.microfocus.application.automation.tools.sv.runner.AbstractSvRemoteRunner;
import com.microfocus.application.automation.tools.sv.runner.AbstractSvRunBuilder;
import com.microfocus.application.automation.tools.sv.runner.AbstractSvRunDescriptor;
import com.microfocus.application.automation.tools.sv.runner.ServiceInfo;
import com.microfocus.sv.svconfigurator.processor.IUndeployProcessor;
import com.microfocus.sv.svconfigurator.processor.UndeployProcessor;
import com.microfocus.sv.svconfigurator.processor.UndeployProcessorInput;
import com.microfocus.sv.svconfigurator.serverclient.ICommandExecutor;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Deletes selected virtual service from server
 */
public class SvUndeployBuilder extends AbstractSvRunBuilder<SvUndeployModel> {

    @DataBoundConstructor
    public SvUndeployBuilder(String serverName, boolean continueIfNotDeployed, boolean force, SvServiceSelectionModel serviceSelection) {
        super(new SvUndeployModel(serverName, continueIfNotDeployed, force, serviceSelection));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    protected RemoteRunner getRemoteRunner(@Nonnull FilePath workspace, TaskListener listener, SvServerSettingsModel server) {
        return new RemoteRunner(model, workspace, listener, server);
    }

    private static class RemoteRunner extends AbstractSvRemoteRunner<SvUndeployModel> {

        private RemoteRunner(SvUndeployModel model, FilePath workspace, TaskListener listener, SvServerSettingsModel server) {
            super(listener, model, workspace, server);
        }

        @Override
        public String call() throws Exception {
            PrintStream logger = listener.getLogger();

            IUndeployProcessor processor = new UndeployProcessor(null);
            ICommandExecutor exec = createCommandExecutor();
            for (ServiceInfo service : getServiceList(model.isContinueIfNotDeployed(), logger, workspace)) {
                logger.printf("  Undeploying service '%s' [%s] %n", service.getName(), service.getId());
                UndeployProcessorInput undeployProcessorInput = new UndeployProcessorInput(model.isForce(), null, service.getId());
                processor.process(undeployProcessorInput, exec);
            }

            return null;
        }
    }
    @Override
    protected void logConfig(PrintStream logger, String prefix) {
        super.logConfig(logger, prefix);
        logger.println(prefix + "Continue if not deployed: " + model.isContinueIfNotDeployed());
    }

    @Extension
    public static final class DescriptorImpl extends AbstractSvRunDescriptor {

        public DescriptorImpl() {
            super("SV: Undeploy Virtual Service");
        }
    }
}
