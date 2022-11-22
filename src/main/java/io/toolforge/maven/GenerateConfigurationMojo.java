/*-
 * =================================LICENSE_START==================================
 * emoji4j-maven-plugin
 * ====================================SECTION=====================================
 * Copyright (C) 2022 Andy Boothe
 * ====================================SECTION=====================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==================================LICENSE_END===================================
 */
package io.toolforge.maven;

import java.io.File;
import java.io.IOException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.toolforge.spi.model.Manifest;

/**
 * Generates the graphemes.json file used by emoji4j-core
 */
@Mojo(name = "configuration", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateConfigurationMojo extends AbstractMojo {
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  // Current maven project
  @Parameter(defaultValue = "${project}", readonly = true)
  protected MavenProject project;

  // Current maven project
  @Parameter(property = "session")
  protected MavenSession session;

  // Current mojo execution
  @Parameter(property = "mojoExecution")
  protected MojoExecution execution;

  @Parameter(property = "toolforge.target.directory", defaultValue = "target/generated-sources")
  private String outputDirectory;

  @Parameter(property = "toolforge.location.manifest", defaultValue = "manifest.yml")
  private String manifestLocation;

  @Parameter(property = "toolforge.target.package")
  private String outputPackage;

  @Parameter(property = "toolforge.target.class", defaultValue = "Configuration")
  private String outputClassName;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    File basedir = session.getCurrentProject().getBasedir();

    File manifestFile = new File(basedir, this.manifestLocation);

    File outputDirectory = new File(basedir, this.outputDirectory);

    Manifest manifest;
    try {
      manifest = YAML.readValue(manifestFile, Manifest.class);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to read manifest from " + this.manifestLocation, e);
    }

    ClassName configurationName = ClassName.get(outputPackage, outputClassName);

    TypeSpec configurationType =
        new CodeGenerator(configurationName).generateConfiguration(manifest);

    JavaFile configurationFile = JavaFile.builder(outputPackage, configurationType).build();

    try {
      configurationFile.writeTo(outputDirectory);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed to write generated source file to " + this.outputDirectory, e);
    }

    session.getCurrentProject().addCompileSourceRoot(this.outputDirectory);
  }
}
