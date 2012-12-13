/*
  Copyright 2010 Regents of the University of Colorado.
 All rights reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.uimafit.factory;

import static org.uimafit.factory.ConfigurationParameterFactory.createConfigurationData;
import static org.uimafit.factory.ConfigurationParameterFactory.ensureParametersComeInPairs;
import static org.uimafit.factory.ExternalResourceFactory.bindExternalResource;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.uima.Constants;
import org.apache.uima.flow.FlowController;
import org.apache.uima.flow.FlowControllerDescription;
import org.apache.uima.flow.impl.FlowControllerDescription_impl;
import org.apache.uima.resource.ExternalResourceDependency;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.ConfigurationParameter;
import org.apache.uima.resource.metadata.ResourceMetaData;
import org.uimafit.component.initialize.ExternalResourceInitializer;
import org.uimafit.factory.ConfigurationParameterFactory.ConfigurationData;

/**
 * @author Fabio Mancinelli, Philip Ogren
 */

public final class FlowControllerFactory {
	private FlowControllerFactory() {
		// This class is not meant to be instantiated
	}

	/**
	 * Creates a new FlowControllerDescription for a given class and configuration data
	 * 
	 * @param configurationData
	 *            should be configuration parameter name / value pairs.
	 */
	public static FlowControllerDescription createFlowControllerDescription(
			Class<? extends FlowController> flowControllerClass, Object... configurationData)
			throws ResourceInitializationException {

		ensureParametersComeInPairs(configurationData);

		// Extract ExternalResourceDescriptions from configurationData
		// <ParamterName, ExternalResourceDescription> will be stored in this map
		Map<String, ExternalResourceDescription> externalResources = 
				ExternalResourceFactory.extractExternalResourceParameters(configurationData);

		// Create description normally
		ConfigurationData cdata = createConfigurationData(configurationData);
		return createFlowControllerDescription(flowControllerClass, cdata.configurationParameters,
				cdata.configurationValues, externalResources);
	}

	public static FlowControllerDescription createFlowControllerDescription(
			Class<? extends FlowController> flowControllerClass,
			ConfigurationParameter[] configurationParameters, Object[] configurationValues)
			throws ResourceInitializationException {
		return createFlowControllerDescription(flowControllerClass, configurationParameters,
				configurationValues, null);
	}
	
	/**
	 * Creates a new FlowControllerDescription for a given class and configuration parameters with
	 * values
	 */
	public static FlowControllerDescription createFlowControllerDescription(
			Class<? extends FlowController> flowControllerClass,
			ConfigurationParameter[] configurationParameters, Object[] configurationValues,
			Map<String, ExternalResourceDescription> externalResources)
			throws ResourceInitializationException {
		FlowControllerDescription desc = new FlowControllerDescription_impl();
		desc.setFrameworkImplementation(Constants.JAVA_FRAMEWORK_NAME);
		desc.setImplementationName(flowControllerClass.getName());

		ConfigurationData reflectedConfigurationData = ConfigurationParameterFactory
				.createConfigurationData(flowControllerClass);
		ResourceCreationSpecifierFactory.setConfigurationParameters(desc,
				reflectedConfigurationData.configurationParameters,
				reflectedConfigurationData.configurationValues);
		if (configurationParameters != null) {
			ResourceCreationSpecifierFactory.setConfigurationParameters(desc,
					configurationParameters, configurationValues);
		}

		ResourceMetaData meta = desc.getMetaData();
		meta.setName(flowControllerClass.getName());
		meta.setVendor(flowControllerClass.getPackage().toString());
		meta.setDescription("Descriptor automatically generated by uimaFIT");
		meta.setVersion("unknown");

		// Extract external resource dependencies
		Collection<ExternalResourceDependency> deps = ExternalResourceInitializer
				.getResourceDeclarations(flowControllerClass).values();
		desc.setExternalResourceDependencies(deps.toArray(new ExternalResourceDependency[deps
				.size()]));

		// Bind External Resources
		if (externalResources != null) {
			for (Entry<String, ExternalResourceDescription> e : externalResources.entrySet()) {
				bindExternalResource(desc, e.getKey(), e.getValue());
			}
		}

		return desc;
	}

}