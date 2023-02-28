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

package com.microfocus.application.automation.tools.commonResultUpload.xmlreader.configloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microfocus.application.automation.tools.commonResultUpload.CommonUploadLogger;
import com.microfocus.application.automation.tools.commonResultUpload.service.CustomizationService;
import com.microfocus.application.automation.tools.commonResultUpload.service.UDFTranslator;
import com.microfocus.application.automation.tools.commonResultUpload.xmlreader.model.EntitiesFieldMap;

import java.io.IOException;
import java.util.Map;

import static com.microfocus.application.automation.tools.results.service.almentities.AlmTest.TEST_TYPE;
import static com.microfocus.application.automation.tools.results.service.almentities.AlmTestSet.TESTSET_SUB_TYPE_ID;

public class EntitiesFieldMapLoader {

    private static final String ILLEGAL = "Illegal ";
    private static final String[] TEST_SET_REQUIRED_FIELDS = new String[]{"root", "name", TESTSET_SUB_TYPE_ID};
    private static final String[] RUN_REQUIRED_FIELDS = new String[]{"root"};
    private static String[] testRquiredFields;

    private EntitiesFieldMapLoader() {

    }

    public static EntitiesFieldMap load(String yamlContent, CommonUploadLogger logger, CustomizationService cs, boolean isCreateNewTest) {
        if (isCreateNewTest) {
            testRquiredFields = new String[]{"root",  "name", TEST_TYPE};
        } else {
            testRquiredFields = new String[]{"root", TEST_TYPE};
        }
        return load(yamlContent, logger, cs);
    }

    public static EntitiesFieldMap load(String yamlContent, CommonUploadLogger logger, CustomizationService cs) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        EntitiesFieldMap entitiesFieldMap;
        try {
            entitiesFieldMap = mapper.readValue(yamlContent, EntitiesFieldMap.class);
        } catch (IOException | RuntimeException e) {
            logger.error("Field mapping is not in right format. " + e.getMessage());
            return null;
        }
        if (!validateFieldMapping(entitiesFieldMap, logger, cs)) {
            return null;
        }
        return entitiesFieldMap;
    }

    private static boolean validateFieldMapping(EntitiesFieldMap entitiesFieldMap,
                                                CommonUploadLogger logger, CustomizationService cs) {
        if (!checkRequiredFields(entitiesFieldMap, logger)) {
            return false;
        }
        if (!checkEntitiesFieldName(cs, entitiesFieldMap, logger)) {
            return false;
        }
        return checkEntitiesSubtype(cs, entitiesFieldMap, logger);
    }

    private static boolean checkEntitiesSubtype(CustomizationService cs, EntitiesFieldMap entitiesFieldMap,
                                                CommonUploadLogger logger) {
        if (!checkSubtypeIdLlegal(cs, entitiesFieldMap.getTestset(), CustomizationService.TEST_SET_ENTITY_NAME, logger)) {
            return false;
        }
        return checkSubtypeIdLlegal(cs, entitiesFieldMap.getTest(), CustomizationService.TEST_ENTITY_NAME, logger);
    }

    private static boolean checkSubtypeIdLlegal(CustomizationService cs, Map<String, String> fieldMap,
                                                String entityName, CommonUploadLogger logger) {
        String subtypeId = fieldMap.get("subtype-id");
        subtypeId = subtypeId.substring(2, subtypeId.length());
        Map<String, String> subtypeMap = cs.getEntitySubTypes(entityName);
        if (!containsValue(subtypeId, subtypeMap)) {
            logger.error(ILLEGAL + entityName + " subtype-id: " + subtypeId);
            return false;
        }
        return true;
    }

    private static boolean checkRequiredFields(EntitiesFieldMap entitiesFieldMap, CommonUploadLogger logger) {
        for (String field : TEST_SET_REQUIRED_FIELDS) {
            if (!entitiesFieldMap.getTestset().containsKey(field)) {
                logger.error(field + " should be set in test-set's field mapping.");
                return false;
            }
        }
        for (String field : testRquiredFields) {
            if (!entitiesFieldMap.getTest().containsKey(field)) {
                logger.error(field + " should be set in test's field mapping.");
                return false;
            }
        }
        for (String field : RUN_REQUIRED_FIELDS) {
            if (!entitiesFieldMap.getRun().containsKey(field)) {
                logger.error(field + " should be set in run's field mapping.");
                return false;
            }
        }
        return true;
    }

    private static boolean checkEntitiesFieldName(CustomizationService cs, EntitiesFieldMap entitiesFieldMap,
                                                  CommonUploadLogger logger) {
        if (!checkFieldNameLegal(cs, CustomizationService.TEST_SET_ENTITY_NAME,
                entitiesFieldMap.getTestset(), logger)) {
            return false;
        }
        if (!checkFieldNameLegal(cs, CustomizationService.TEST_ENTITY_NAME,
                entitiesFieldMap.getTest(), logger)) {
            return false;
        }
        return checkFieldNameLegal(cs, CustomizationService.RUN_ENTITY_NAME,
                entitiesFieldMap.getRun(), logger);
    }

    private static boolean checkFieldNameLegal(CustomizationService cs,
                                               String entityName, Map<String, String> fieldMap,
                                               CommonUploadLogger logger) {
        Map<String, String> entityFields = cs.getEntityFields(entityName);
        for (String fieldName : fieldMap.keySet()) {
            // Check if field name exists
            if (!containsValue(fieldName, entityFields) && !fieldName.equals("root") && !fieldName.contains("|")) {
                logger.error(ILLEGAL + entityName + " field name: " + fieldName);
                return false;
            }
            // Check if field label exists
            if (fieldName.startsWith(UDFTranslator.UDF_PREFIX)) {
                String label = fieldName.substring(
                        fieldName.indexOf(UDFTranslator.UDF_PREFIX) + UDFTranslator.UDF_PREFIX.length());
                if (!entityFields.containsKey(label)) {
                    logger.error(ILLEGAL + entityName + " label: " + fieldName);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsValue(String value, Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            if (value.equals(map.get(key))) {
                return true;
            }
        }
        return false;
    }
}
