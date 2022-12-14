/*-
 * =================================LICENSE_START==================================
 * toolforge-maven-plugin
 * ====================================SECTION=====================================
 * Copyright (C) 2022 ToolForge
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import com.google.common.io.Resources;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.toolforge.spi.model.BooleanParameterDefinition;
import io.toolforge.spi.model.ContainerSize;
import io.toolforge.spi.model.ContainerVersionSecret;
import io.toolforge.spi.model.ContainerVersionVariable;
import io.toolforge.spi.model.DateParameterDefinition;
import io.toolforge.spi.model.EnumerationStringDomain;
import io.toolforge.spi.model.FloatParameterDefinition;
import io.toolforge.spi.model.IntParameterDefinition;
import io.toolforge.spi.model.ManifestEnvironment;
import io.toolforge.spi.model.ParameterType;
import io.toolforge.spi.model.PatternStringDomain;
import io.toolforge.spi.model.Slot;
import io.toolforge.spi.model.StringDomainType;
import io.toolforge.spi.model.StringParameterDefinition;
import io.toolforge.spi.model.ToolManifest;
import io.toolforge.spi.model.expr.date.RelativeDateExpr;
import io.toolforge.spi.model.expr.date.RelativeDateExpr.DateUnit;
import io.toolforge.spi.model.expr.date.TodayDateExpr;

public class CodeGeneratorTest {
  /**
   * A complex manifest should generate code just so.
   */
  @Test
  public void smokeTest() throws IOException {
    ClassName className = ClassName.get("com.example", "Configuration");

    ToolManifest manifest = (ToolManifest) new ToolManifest()
        .addParametersItem(new BooleanParameterDefinition()._default(true)
            .type(ParameterType.BOOLEAN).name("exampleBoolean")
            .description("This is an example boolean field.").required(true))
        .addParametersItem(new IntParameterDefinition()._default(10L).minimum(0L).maximum(100L)
            .type(ParameterType.INT).name("exampleInt").description("This is an example int field.")
            .required(true))
        .addParametersItem(new FloatParameterDefinition()._default(10.0).minimum(0.0).maximum(100.0)
            .type(ParameterType.FLOAT).name("exampleFloat")
            .description("This is an example float field.").required(true))
        .addParametersItem(new StringParameterDefinition()
            .domain(new EnumerationStringDomain().addValuesItem("alpha").addValuesItem("bravo")
                .type(StringDomainType.ENUMERATION))
            ._default("alpha").type(ParameterType.STRING).name("exampleEnumString")
            .description("This is an example enum string field.").required(true))
        .addParametersItem(new StringParameterDefinition()
            .domain(new PatternStringDomain().pattern("^hel*o$").type(StringDomainType.PATTERN))
            ._default("hello").type(ParameterType.STRING).name("examplePatternString")
            .description("This is an example pattern string field.").required(true))
        .addParametersItem(new DateParameterDefinition()._default(TodayDateExpr.INSTANCE)
            .minimum(RelativeDateExpr.of(-1, DateUnit.WEEK))
            .maximum(RelativeDateExpr.of(+1, DateUnit.WEEK)).type(ParameterType.DATE)
            .name("exampleDate").description("This is an example date field.").required(true))
        .addInputsItem(new Slot().name("input").description("This is the first input.")
            .addExtensionsItem("csv"))
        .addOutputsItem(new Slot().name("output").description("This is the first output.")
            .addExtensionsItem("csv").addExtensionsItem("xlsx"))
        .environment(new ManifestEnvironment().size(ContainerSize.MEDIUM)
            .addVariablesItem(new ContainerVersionVariable().name("EXAMPLE_VARIABLE_1")
                .required(true).description("This is variable 1.")._default("hello"))
            .addSecretsItem(new ContainerVersionSecret().name("EXAMPLE_SECRET_1").required(false)
                .description("This is secret 1.").example("world")));

    TypeSpec configurationType = new CodeGenerator(className).generateConfiguration(manifest);

    JavaFile javaFile = JavaFile.builder("com.example", configurationType).build();

    String observed;
    try (StringWriter w = new StringWriter()) {
      javaFile.writeTo(w);
      observed = w.toString();
    }

    String expected = Resources.toString(Resources.getResource("com/example/Configuration.java"),
        StandardCharsets.UTF_8);

    assertThat(observed, is(expected));
  }
}
