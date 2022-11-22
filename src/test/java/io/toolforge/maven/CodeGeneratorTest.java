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
import io.toolforge.spi.model.DateParameterDefinition;
import io.toolforge.spi.model.EnumerationStringDomain;
import io.toolforge.spi.model.FloatParameterDefinition;
import io.toolforge.spi.model.IntParameterDefinition;
import io.toolforge.spi.model.Manifest;
import io.toolforge.spi.model.ManifestEnvironment;
import io.toolforge.spi.model.ManifestEnvironmentSecret;
import io.toolforge.spi.model.ManifestEnvironmentSize;
import io.toolforge.spi.model.ManifestEnvironmentVariable;
import io.toolforge.spi.model.ParameterType;
import io.toolforge.spi.model.PatternStringDomain;
import io.toolforge.spi.model.StringDomainType;
import io.toolforge.spi.model.StringParameterDefinition;
import io.toolforge.spi.model.ToolInput;
import io.toolforge.spi.model.ToolOutput;
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

    Manifest manifest = new Manifest()
        .environment(new ManifestEnvironment().size(ManifestEnvironmentSize.MEDIUM)
            .addVariablesItem(new ManifestEnvironmentVariable().name("EXAMPLE_VARIABLE_1")
                .required(true).description("This is variable 1.")._default("hello"))
            .addSecretsItem(new ManifestEnvironmentSecret().name("EXAMPLE_SECRET_1").required(false)
                .description("This is secret 1.").example("world")))
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
        .addInputsItem(new ToolInput().name("input").description("This is the first input.")
            .addExtensionsItem("csv"))
        .addOutputsItem(new ToolOutput().name("output").description("This is the first output.")
            .addExtensionsItem("csv").addExtensionsItem("xlsx"));

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
