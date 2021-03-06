/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.uima.fit.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.maven.util.Util;
import org.apache.uima.resource.ResourceCreationSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.metadata.ProcessingResourceMetaData;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.codehaus.plexus.util.FileUtils;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.xml.sax.SAXException;

/**
 * Generate descriptor files for uimaFIT-based UIMA components.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class GenerateDescriptorsMojo extends AbstractMojo {
  @Component
  private MavenProject project;

  @Component
  private BuildContext buildContext;

  private ClassLoader componentLoader;

  /**
   * Path where the generated resources are written.
   */
  @Parameter(defaultValue = "${project.build.directory}/classes", required = true)
  private File outputDirectory;
  
  /**
   * Skip generation of META-INF/org.apache.uima.fit/components.txt
   */
  @Parameter(defaultValue = "false", required = true)
  private boolean skipComponentsManifest;

  /**
   * Source file encoding.
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}", required = true)
  private String encoding;

  enum TypeSystemSerialization {
    NONE, EMBEDDED
  }

  /**
   * Mode of adding type systems found on the classpath via the uimaFIT detection mechanism at
   * compile time to the generated descriptor. By default, no type systems are added.
   */
  @Parameter(defaultValue = "NONE")
  private TypeSystemSerialization addTypeSystemDescriptions;

  @Override
  public void execute() throws MojoExecutionException {
    // add the generated sources to the build
    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs();
      buildContext.refresh(outputDirectory);
    }
    
    // Get the compiled classes from this project
    String[] files = FileUtils.getFilesFromExtension(project.getBuild().getOutputDirectory(),
            new String[] { "class" });

    componentLoader = Util.getClassloader(project, getLog());

    // List of components that is later written to META-INF/org.apache.uima.fit/components.txt
    StringBuilder componentsManifest = new StringBuilder();

    int countGenerated = 0;
    for (String file : files) {
      String base = file.substring(0, file.length() - 6);
      String clazzPath = base.substring(project.getBuild().getOutputDirectory().length() + 1);
      String clazzName = clazzPath.replace(File.separator, ".");
      try {
        Class clazz = componentLoader.loadClass(clazzName);
        
        // Do not generate descriptors for abstract classes, they cannot be instantiated.
        if (Modifier.isAbstract(clazz.getModifiers())) {
          continue;
        }
        
        ResourceCreationSpecifier desc = null;
        ProcessingResourceMetaData metadata = null;
        switch (Util.getType(componentLoader, clazz)) {
          case ANALYSIS_ENGINE:
            AnalysisEngineDescription aeDesc = AnalysisEngineFactory.createEngineDescription(clazz);
            metadata = aeDesc.getAnalysisEngineMetaData();
            desc = aeDesc;
            break;
          case COLLECTION_READER:
            CollectionReaderDescription crDesc = CollectionReaderFactory
                    .createReaderDescription(clazz);
            metadata = crDesc.getCollectionReaderMetaData();
            desc = crDesc;
          default:
            // Do nothing
        }

        if (desc != null) {
          switch (addTypeSystemDescriptions) {
            case EMBEDDED:
              embedTypeSystems(metadata);
              break;
            case NONE: // fall-through
            default:
              // Do nothing
          }

          File out = new File(outputDirectory, clazzPath + ".xml");
          out.getParentFile().mkdirs();
          toXML(desc, out.getPath());
          countGenerated++;
          
          // Remember component
          componentsManifest.append("classpath*:").append(clazzPath + ".xml").append('\n');
        }
      } catch (SAXException e) {
        getLog().warn("Cannot serialize descriptor for [" + clazzName + "]", e);
      } catch (IOException e) {
        getLog().warn("Cannot write descriptor for [" + clazzName + "]", e);
      } catch (ClassNotFoundException e) {
        getLog().warn("Cannot analyze class [" + clazzName + "]", e);
      } catch (ResourceInitializationException e) {
        getLog().warn("Cannot generate descriptor for [" + clazzName + "]", e);
      }
    }
    
    getLog().info(
            "Generated " + countGenerated + " descriptor" + (countGenerated != 1 ? "s." : "."));
    
    // Write META-INF/org.apache.uima.fit/components.txt unless skipped and unless there are no
    // components
    if (!skipComponentsManifest && componentsManifest.length() > 0) {
      File path = new File(outputDirectory, "META-INF/org.apache.uima.fit/components.txt");
      FileUtils.mkdir(path.getParent());
      try {
        FileUtils.fileWrite(path.getPath(), encoding, componentsManifest.toString());
      } catch (IOException e) {
        throw new MojoExecutionException("Cannot write components manifest to [" + path + "]"
                + ExceptionUtils.getRootCauseMessage(e), e);
      }
    }
  }

  private void embedTypeSystems(ProcessingResourceMetaData metadata)
          throws ResourceInitializationException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(componentLoader);
    try {
      TypeSystemDescriptionFactory.forceTypeDescriptorsScan();
      TypeSystemDescription tsDesc = TypeSystemDescriptionFactory.createTypeSystemDescription();
      metadata.setTypeSystem(tsDesc);
    } finally {
      Thread.currentThread().setContextClassLoader(classLoader);
    }
  }

  /**
   * Save descriptor XML to file system.
   */
  private void toXML(ResourceSpecifier aDesc, String aFilename) throws SAXException, IOException {
    OutputStream os = null;
    try {
      File out = new File(aFilename);
      getLog().debug("Writing descriptor to: " + out);
      os = new FileOutputStream(out);
      aDesc.toXML(os);
    } finally {
      IOUtils.closeQuietly(os);
    }
  }
}
