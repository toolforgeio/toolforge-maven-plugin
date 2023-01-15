package com.example;

import com.sigpwned.discourse.core.annotation.Configurable;
import com.sigpwned.discourse.core.annotation.EnvironmentParameter;
import com.sigpwned.discourse.core.annotation.OptionParameter;
import io.toolforge.toolforge4j.io.InputSource;
import io.toolforge.toolforge4j.io.OutputSink;
import java.lang.Boolean;
import java.lang.Double;
import java.lang.IllegalArgumentException;
import java.lang.Long;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Configurable
public final class Configuration {
  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

  @SuppressWarnings("serial")
  private static final Set<String> EXAMPLE_ENUM_STRING_ENUMERATION = Collections.unmodifiableSet(new HashSet<String>() {{
    add("alpha");
    add("bravo");
  }});

  private static final Pattern EXAMPLE_PATTERN_STRING_PATTERN = Pattern.compile("^hel*o$");

  @EnvironmentParameter(
      variableName = "EXAMPLE_VARIABLE_1",
      required = true,
      description = "This is variable 1."
  )
  public String exampleVariable1 = "hello";

  @EnvironmentParameter(
      variableName = "EXAMPLE_SECRET_1",
      required = false,
      description = "This is secret 1."
  )
  public String exampleSecret1;

  @OptionParameter(
      longName = "exampleBoolean",
      description = "This is an example boolean field.",
      required = true
  )
  public Boolean exampleBoolean = Boolean.valueOf(true);

  @OptionParameter(
      longName = "exampleInt",
      description = "This is an example int field.",
      required = true
  )
  public Long exampleInt = Long.valueOf(10);

  @OptionParameter(
      longName = "exampleFloat",
      description = "This is an example float field.",
      required = true
  )
  public Double exampleFloat = Double.valueOf(10.0);

  @OptionParameter(
      longName = "exampleEnumString",
      description = "This is an example enum string field.",
      required = true
  )
  public String exampleEnumString = "alpha";

  @OptionParameter(
      longName = "examplePatternString",
      description = "This is an example pattern string field.",
      required = true
  )
  public String examplePatternString = "hello";

  @OptionParameter(
      longName = "exampleDate",
      description = "This is an example date field.",
      required = true
  )
  public LocalDate exampleDate = TODAY;

  @OptionParameter(
      longName = "input",
      required = true,
      description = "This is the first input."
  )
  public InputSource input;

  @OptionParameter(
      longName = "output.csv",
      required = true,
      description = "This is the first output."
  )
  public OutputSink outputCsv;

  @OptionParameter(
      longName = "output.xlsx",
      required = true,
      description = "This is the first output."
  )
  public OutputSink outputXlsx;

  public Configuration validate() {
    // No validation to do for exampleBoolean
    if(exampleInt < 0) {
      throw new IllegalArgumentException("exampleInt must be greater than or equal to 0");
    }
    if(exampleInt > 100) {
      throw new IllegalArgumentException("exampleInt must be less than or equal to 100");
    }
    if(exampleFloat < 0.0) {
      throw new IllegalArgumentException("exampleFloat must be greater than or equal to 0.000000");
    }
    if(exampleFloat > 100.0) {
      throw new IllegalArgumentException("exampleFloat must be less than or equal to 100.000000");
    }
    if(!EXAMPLE_ENUM_STRING_ENUMERATION.contains(exampleEnumString)) {
      throw new IllegalArgumentException("exampleEnumString must be one of: alpha, bravo");
    }
    if(!EXAMPLE_PATTERN_STRING_PATTERN.matcher(examplePatternString).matches()) {
      throw new IllegalArgumentException("examplePatternString must match the pattern `^hel*o$'");
    }
    if(exampleDate.isBefore(TODAY.plusWeeks(-1))) {
      throw new IllegalArgumentException("exampleDate must be greater than or equal to " + TODAY.plusWeeks(-1));
    }
    if(exampleDate.isAfter(TODAY.plusWeeks(1))) {
      throw new IllegalArgumentException("exampleDate must be less than or equal to " + TODAY.plusWeeks(1));
    }
    return this;
  }
}
