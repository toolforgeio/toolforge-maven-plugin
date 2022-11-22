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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import com.sigpwned.discourse.core.annotation.Configurable;
import com.sigpwned.discourse.core.annotation.EnvironmentParameter;
import com.sigpwned.discourse.core.annotation.OptionParameter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import io.toolforge.maven.com.google.common.base.CaseFormat;
import io.toolforge.spi.model.BooleanParameterDefinition;
import io.toolforge.spi.model.DateExpr;
import io.toolforge.spi.model.DateParameterDefinition;
import io.toolforge.spi.model.EnumerationStringDomain;
import io.toolforge.spi.model.FloatParameterDefinition;
import io.toolforge.spi.model.IntParameterDefinition;
import io.toolforge.spi.model.Manifest;
import io.toolforge.spi.model.ManifestEnvironmentSecret;
import io.toolforge.spi.model.ManifestEnvironmentVariable;
import io.toolforge.spi.model.ParameterDefinition;
import io.toolforge.spi.model.PatternStringDomain;
import io.toolforge.spi.model.StringParameterDefinition;
import io.toolforge.spi.model.ToolInput;
import io.toolforge.spi.model.ToolOutput;
import io.toolforge.spi.model.expr.date.AbsoluteDateExpr;
import io.toolforge.spi.model.expr.date.RelativeDateExpr;
import io.toolforge.spi.model.expr.date.TodayDateExpr;
import io.toolforge.toolforge4j.io.InputSource;
import io.toolforge.toolforge4j.io.OutputSink;

public class CodeGenerator {
  public static CodeBlock TODAY = CodeBlock.of("$T.now($T.UTC)", LocalDate.class, ZoneOffset.class);

  private final ClassName className;

  public CodeGenerator(ClassName className) {
    this.className = requireNonNull(className);
  }

  /**
   * Generates a type declaration for the data fields in the given {@link Manifest}.
   */
  public TypeSpec generateConfiguration(Manifest manifest) {
    TypeSpec.Builder configurationBuilder = TypeSpec.classBuilder(getClassName().simpleName())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL).addAnnotation(Configurable.class);

    configurationBuilder.addField(FieldSpec
        .builder(LocalDate.class, "TODAY", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .initializer(TODAY).build());

    for (ParameterDefinition parameter : manifest.getParameters())
      generatePreparation(parameter).ifPresent(configurationBuilder::addField);

    if (manifest.getEnvironment() != null && manifest.getEnvironment().getVariables() != null) {
      for (ManifestEnvironmentVariable variable : manifest.getEnvironment().getVariables()) {
        configurationBuilder.addField(generateVariableField(variable));
      }
    }

    if (manifest.getEnvironment() != null && manifest.getEnvironment().getSecrets() != null) {
      for (ManifestEnvironmentSecret secret : manifest.getEnvironment().getSecrets()) {
        configurationBuilder.addField(generateSecretField(secret));
      }
    }

    for (ParameterDefinition parameter : manifest.getParameters())
      configurationBuilder.addField(generateParameterField(parameter));

    for (ToolInput input : manifest.getInputs())
      configurationBuilder.addField(generateInputField(input));

    for (ToolOutput output : manifest.getOutputs())
      for (String extension : output.getExtensions())
        configurationBuilder.addField(generateOutputExtensionField(output, extension));

    configurationBuilder.addMethod(generateValidateMethod(manifest));

    return configurationBuilder.build();
  }

  protected FieldSpec generateInputField(ToolInput input) {
    return FieldSpec.builder(InputSource.class, input.getName(), Modifier.PUBLIC)
        .addAnnotation(AnnotationSpec.builder(OptionParameter.class)
            .addMember("longName", "$S", input.getName()).addMember("required", "$L", true)
            .addMember("description", CodeBlock.of("$S", input.getDescription())).build())
        .build();

  }

  protected FieldSpec generateOutputExtensionField(ToolOutput output, String extension) {
    return FieldSpec
        .builder(OutputSink.class,
            output.getName() + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, extension),
            Modifier.PUBLIC)
        .addAnnotation(AnnotationSpec.builder(OptionParameter.class)
            .addMember("longName", "$S", output.getName() + "." + extension)
            .addMember("required", "$L", true)
            .addMember("description", CodeBlock.of("$S", output.getDescription())).build())
        .build();

  }

  protected FieldSpec generateVariableField(ManifestEnvironmentVariable variable) {
    return FieldSpec.builder(String.class, variable.getName(), Modifier.PUBLIC)
        .initializer(CodeBlock.of("$S", variable.getDefault()))
        .addAnnotation(AnnotationSpec.builder(EnvironmentParameter.class)
            .addMember("variableName", "$S", variable.getName())
            .addMember("required", "$L", variable.getRequired())
            .addMember("description", "$S", variable.getDescription()).build())
        .build();
  }

  protected FieldSpec generateSecretField(ManifestEnvironmentSecret variable) {
    return FieldSpec.builder(String.class, variable.getName(), Modifier.PUBLIC)
        .addAnnotation(AnnotationSpec.builder(EnvironmentParameter.class)
            .addMember("variableName", "$S", variable.getName())
            .addMember("required", "$L", variable.getRequired())
            .addMember("description", "$S", variable.getDescription()).build())
        .build();

  }

  protected FieldSpec generateParameterField(ParameterDefinition parameter) {
    FieldSpec.Builder fieldBuilder;
    switch (parameter.getType()) {
      case BOOLEAN:
        BooleanParameterDefinition booleanParameter = (BooleanParameterDefinition) parameter;
        fieldBuilder = FieldSpec.builder(Boolean.class,
            parameterNameToLowerCamel(booleanParameter.getName()), Modifier.PUBLIC);
        if (booleanParameter.getDefault() != null)
          fieldBuilder =
              fieldBuilder.initializer("Boolean.valueOf($L)", booleanParameter.getDefault());
        break;
      case DATE:
        DateParameterDefinition dateParameter = (DateParameterDefinition) parameter;
        fieldBuilder = FieldSpec.builder(LocalDate.class,
            parameterNameToLowerCamel(dateParameter.getName()), Modifier.PUBLIC);
        if (dateParameter.getDefault() != null)
          fieldBuilder = fieldBuilder.initializer(generateDateExpr(dateParameter.getDefault()));
        break;
      case FLOAT:
        FloatParameterDefinition floatParameter = (FloatParameterDefinition) parameter;
        fieldBuilder = FieldSpec.builder(Double.class,
            parameterNameToLowerCamel(floatParameter.getName()), Modifier.PUBLIC);
        if (floatParameter.getDefault() != null)
          fieldBuilder =
              fieldBuilder.initializer("Double.valueOf($L)", floatParameter.getDefault());
        break;
      case INT:
        IntParameterDefinition intParameter = (IntParameterDefinition) parameter;
        fieldBuilder = FieldSpec.builder(Long.class,
            parameterNameToLowerCamel(intParameter.getName()), Modifier.PUBLIC);
        if (intParameter.getDefault() != null)
          fieldBuilder = fieldBuilder.initializer("Long.valueOf($L)", intParameter.getDefault());
        break;
      case STRING:
        StringParameterDefinition stringParameter = (StringParameterDefinition) parameter;
        fieldBuilder = FieldSpec.builder(String.class,
            parameterNameToLowerCamel(stringParameter.getName()), Modifier.PUBLIC);
        if (stringParameter.getDefault() != null)
          fieldBuilder = fieldBuilder.initializer("$S", stringParameter.getDefault());
        break;
      default:
        throw new AssertionError(parameter.getType());
    }
    return fieldBuilder.addAnnotation(AnnotationSpec.builder(OptionParameter.class)
        .addMember("longName", "$S", parameter.getName())
        .addMember("description", "$S", parameter.getDescription())
        .addMember("required", "$L", parameter.getRequired()).build()).build();

  }

  protected Optional<FieldSpec> generatePreparation(ParameterDefinition parameter) {
    FieldSpec result;
    switch (parameter.getType()) {
      case BOOLEAN:
      case DATE:
      case FLOAT:
      case INT:
        // No preparation required.
        result = null;
        break;
      case STRING:
        StringParameterDefinition stringParameter = (StringParameterDefinition) parameter;
        switch (stringParameter.getDomain().getType()) {
          case ENUMERATION:
            EnumerationStringDomain enumerationDomain =
                (EnumerationStringDomain) stringParameter.getDomain();
            result = FieldSpec
                .builder(ParameterizedTypeName.get(Set.class, String.class),
                    parameterNameToUpperUnderscore(stringParameter.getName()) + "_ENUMERATION",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.builder()
                    .add("$T.unmodifiableSet(new $T() {{\n", Collections.class,
                        ParameterizedTypeName.get(HashSet.class, String.class))
                    .indent()
                    .add(enumerationDomain.getValues().stream()
                        .map(v -> CodeBlock.of("add($S);\n", v)).collect(CodeBlock.joining("")))
                    .unindent().add("}})").build())
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "serial").build())
                .build();
            break;
          case PATTERN:
            PatternStringDomain patternDomain = (PatternStringDomain) stringParameter.getDomain();
            result = FieldSpec
                .builder(Pattern.class,
                    parameterNameToUpperUnderscore(stringParameter.getName()) + "_PATTERN",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                    CodeBlock.of("$T.compile($S)", Pattern.class, patternDomain.getPattern()))
                .build();
            break;
          default:
            throw new AssertionError(stringParameter.getDomain().getType());
        }
        break;
      default:
        throw new AssertionError(parameter.getType());
    }
    return Optional.ofNullable(result);
  }


  protected MethodSpec generateValidateMethod(Manifest manifest) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("validate").addModifiers(Modifier.PUBLIC).returns(getClassName());
    for (ParameterDefinition parameter : manifest.getParameters()) {
      methodBuilder.addCode(generateValidationBlock(parameter));
    }
    return methodBuilder.addStatement("return this").build();
  }

  protected CodeBlock generateValidationBlock(ParameterDefinition parameter) {
    CodeBlock result;
    if (parameter.getRequired()) {
      result = generateValidationLogic(parameter);
    } else {
      result = CodeBlock.builder().beginControlFlow("if($L != null)", parameter.getName())
          .add(generateValidationLogic(parameter)).endControlFlow().build();
    }
    return result;
  }

  protected CodeBlock generateValidationLogic(ParameterDefinition parameter) {
    CodeBlock.Builder result = CodeBlock.builder();
    switch (parameter.getType()) {
      case BOOLEAN:
        BooleanParameterDefinition booleanParameter = (BooleanParameterDefinition) parameter;
        result.add("// No validation to do for $L\n", booleanParameter.getName());
        break;
      case DATE:
        DateParameterDefinition dateParameter = (DateParameterDefinition) parameter;
        result = result
            .beginControlFlow("if($L.isBefore($L))", parameter.getName(),
                generateDateExpr(dateParameter.getMinimum()))
            .addStatement(CodeBlock.of("throw new $T($S + $L)", IllegalArgumentException.class,
                String.format("%s must be greater than or equal to ", dateParameter.getName()),
                generateDateExpr(dateParameter.getMinimum())))
            .endControlFlow()
            .beginControlFlow("if($L.isAfter($L))", parameter.getName(),
                generateDateExpr(dateParameter.getMaximum()))
            .addStatement(CodeBlock.of("throw new $T($S + $L)", IllegalArgumentException.class,
                String.format("%s must be less than or equal to ", dateParameter.getName()),
                generateDateExpr(dateParameter.getMaximum())))
            .endControlFlow();
        break;
      case FLOAT:
        FloatParameterDefinition floatParameter = (FloatParameterDefinition) parameter;
        result =
            result.beginControlFlow("if($L < $L)", parameter.getName(), floatParameter.getMinimum())
                .addStatement(CodeBlock.of("throw new $T($S)", IllegalArgumentException.class,
                    String.format("%s must be greater than or equal to %f",
                        floatParameter.getName(), floatParameter.getMinimum())))
                .endControlFlow()
                .beginControlFlow("if($L > $L)", parameter.getName(), floatParameter.getMaximum())
                .addStatement(
                    CodeBlock.of("throw new $T($S)", IllegalArgumentException.class,
                        String.format("%s must be less than or equal to %f",
                            floatParameter.getName(), floatParameter.getMaximum())))
                .endControlFlow();
        break;
      case INT:
        IntParameterDefinition intParameter = (IntParameterDefinition) parameter;
        result =
            result.beginControlFlow("if($L < $L)", parameter.getName(), intParameter.getMinimum())
                .addStatement(CodeBlock.of("throw new $T($S)", IllegalArgumentException.class,
                    String.format("%s must be greater than or equal to %d", intParameter.getName(),
                        intParameter.getMinimum())))
                .endControlFlow()
                .beginControlFlow("if($L > $L)", parameter.getName(), intParameter.getMaximum())
                .addStatement(
                    CodeBlock
                        .of("throw new $T($S)", IllegalArgumentException.class,
                            String.format("%s must be less than or equal to %d",
                                intParameter.getName(), intParameter.getMaximum())))
                .endControlFlow();
        break;
      case STRING:
        StringParameterDefinition stringParameter = (StringParameterDefinition) parameter;
        switch (stringParameter.getDomain().getType()) {
          case ENUMERATION:
            EnumerationStringDomain enumerationDomain =
                (EnumerationStringDomain) stringParameter.getDomain();
            result = result
                .beginControlFlow("if(!$L.contains($L))",
                    parameterNameToUpperUnderscore(stringParameter.getName()) + "_ENUMERATION",
                    stringParameter.getName())
                .addStatement("throw new $T($S)", IllegalArgumentException.class,
                    String.format("%s must be one of: %s", stringParameter.getName(),
                        enumerationDomain.getValues().stream().collect(joining(", "))))
                .endControlFlow();
            break;
          case PATTERN:
            PatternStringDomain patternDomain = (PatternStringDomain) stringParameter.getDomain();
            result =
                result
                    .beginControlFlow("if(!$L.matcher($L).matches())",
                        parameterNameToUpperUnderscore(stringParameter.getName()) + "_PATTERN",
                        stringParameter.getName())
                    .addStatement("throw new $T($S)", IllegalArgumentException.class,
                        String.format("%s must match the pattern `%s'", stringParameter.getName(),
                            patternDomain.getPattern()))
                    .endControlFlow();
            break;
          default:
            throw new AssertionError(stringParameter.getDomain().getType());
        }
        break;
      default:
        throw new AssertionError(parameter.getType());
    }
    return result.build();
  }

  /**
   * Generates a Java expression for the given {@link DataExpr}.
   */
  protected CodeBlock generateDateExpr(DateExpr e) {
    CodeBlock result;
    switch (e.getType()) {
      case ABSOLUTE:
        AbsoluteDateExpr absolute = (AbsoluteDateExpr) e;
        LocalDate value = absolute.getValue();
        result = CodeBlock.of("$T.of($L, $L, $L)", LocalDate.class, value.getYear(),
            value.getMonth(), value.getDayOfMonth());
        break;
      case RELATIVE:
        RelativeDateExpr relative = (RelativeDateExpr) e;

        String methodName;
        switch (relative.getUnit()) {
          case DAY:
            methodName = "plusDays";
            break;
          case MONTH:
            methodName = "plusMonths";
            break;
          case WEEK:
            methodName = "plusWeeks";
            break;
          case YEAR:
            methodName = "plusYears";
            break;
          default:
            throw new AssertionError(relative.getUnit());
        }

        result =
            CodeBlock.of(String.format("$L.%s($L)", methodName), "TODAY", relative.getAmount());
        break;
      case TODAY:
        @SuppressWarnings("unused")
        TodayDateExpr today = (TodayDateExpr) e;
        result = CodeBlock.of("$L", "TODAY");
        break;
      default:
        throw new AssertionError(e.getType());
    }
    return result;
  }

  /**
   * @return the className
   */
  private ClassName getClassName() {
    return className;
  }

  private static String parameterNameToLowerCamel(String name) {
    return Character.isUpperCase(name.charAt(0))
        ? name.substring(0, 1).toLowerCase() + name.substring(1, name.length())
        : name;
  }

  private static String parameterNameToUpperUnderscore(String name) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parameterNameToLowerCamel(name));
  }
}
