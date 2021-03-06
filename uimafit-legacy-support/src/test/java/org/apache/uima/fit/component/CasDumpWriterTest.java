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
package org.apache.uima.fit.component;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.component.CasDumpWriter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 */
public class CasDumpWriterTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void test() throws Exception {
    File outputFile = new File(folder.getRoot(), "dump-output.txt");

    AnalysisEngine writer = AnalysisEngineFactory.createEngine(CasDumpWriter.class,
            CasDumpWriter.PARAM_OUTPUT_FILE, outputFile.getPath());
    JCas jcas = writer.newJCas();
    CasIOUtil.readJCas(jcas, new File("src/test/resources/data/docs/test.xmi"));
    writer.process(jcas);
    assertTrue(outputFile.exists());

    String reference = readFileToString(
            new File("src/test/resources/data/reference/test.xmi.dump"), "UTF-8").trim();
    String actual = readFileToString(outputFile, "UTF-8").trim();
    actual = actual.replaceAll("\r\n", "\n");

    assertEquals(reference, actual);
  }
}
