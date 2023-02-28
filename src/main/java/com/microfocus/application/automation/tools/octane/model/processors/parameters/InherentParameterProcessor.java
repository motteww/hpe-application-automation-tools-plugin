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

package com.microfocus.application.automation.tools.octane.model.processors.parameters;

import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameterType;
import com.microfocus.application.automation.tools.octane.model.ModelFactory;
import hudson.model.*;

import java.util.ArrayList;

/**
 * Created by gullery on 19/02/2015.
 */

public class InherentParameterProcessor extends AbstractParametersProcessor {
	private static final DTOFactory dtoFactory = DTOFactory.getInstance();

	InherentParameterProcessor() {
	}

	@Override
	public CIParameter createParameterConfig(ParameterDefinition pd) {
		CIParameter result;
		if (pd instanceof BooleanParameterDefinition) {
			result = ModelFactory.createParameterConfig(pd, CIParameterType.BOOLEAN);
		} else if (pd instanceof TextParameterDefinition) {
			result = ModelFactory.createParameterConfig(pd, CIParameterType.STRING);
		} else if (pd instanceof StringParameterDefinition) {
			result = ModelFactory.createParameterConfig(pd, CIParameterType.STRING);
		} else if (pd instanceof ChoiceParameterDefinition) {
			ChoiceParameterDefinition choicePd = (ChoiceParameterDefinition) pd;
			result = ModelFactory.createParameterConfig(pd, CIParameterType.STRING, null, new ArrayList<>(choicePd.getChoices()));
		} else if (pd instanceof PasswordParameterDefinition) {
			PasswordParameterDefinition passPd = (PasswordParameterDefinition) pd;
			result = ModelFactory.createParameterConfig(pd, CIParameterType.PASSWORD, passPd.getDefaultValue());
		} else if (pd instanceof FileParameterDefinition) {
			result = ModelFactory.createParameterConfig(pd, CIParameterType.FILE);
		} else {
			result = new UnsupportedParameterProcessor().createParameterConfig(pd);
		}
		return result;
	}

	@Override
	public CIParameter createParameterInstance(ParameterDefinition pd, ParameterValue pv) {
		CIParameter result;
		CIParameter pc = createParameterConfig(pd);
		Object value = pv == null ? null : pv.getValue();
		if (pd instanceof BooleanParameterDefinition) {
			result = ModelFactory.createParameterInstance(pc, value);
		} else if (pd instanceof TextParameterDefinition) {
			result = ModelFactory.createParameterInstance(pc, value);
		} else if (pd instanceof StringParameterDefinition) {
			result = ModelFactory.createParameterInstance(pc, value);
		} else if (pd instanceof ChoiceParameterDefinition) {
			result = ModelFactory.createParameterInstance(pc, value);
		} else if (pd instanceof PasswordParameterDefinition) {
			result = dtoFactory.newDTO(CIParameter.class)
					.setType(pc.getType())
					.setName(pc.getName())
					.setDescription(pc.getDescription())
					.setChoices(pc.getChoices())
					.setDefaultValue(pc.getDefaultValue())
					.setValue(null);
		} else if (pd instanceof FileParameterDefinition) {
			FileParameterValue filePv = (FileParameterValue) pv;
			result = dtoFactory.newDTO(CIParameter.class)
					.setType(pc.getType())
					.setName(pc.getName())
					.setDescription(pc.getDescription())
					.setChoices(pc.getChoices())
					.setDefaultValue(pc.getDefaultValue())
					.setValue(filePv != null ? filePv.getOriginalFileName() : null);
		} else {
			result = new UnsupportedParameterProcessor().createParameterInstance(pd, pv);
		}
		return result;
	}
}
