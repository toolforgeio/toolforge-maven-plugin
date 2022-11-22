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
/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.toolforge.maven.com.google.common.base;

import static java.util.Objects.requireNonNull;
import java.io.Serializable;
import java.util.function.Predicate;

/**
 * Utility class for converting between various ASCII case formats. Behavior is undefined for
 * non-ASCII input.
 *
 * @author Mike Bostock
 * @since 1.0
 */
public enum CaseFormat {
  /** Hyphenated variable naming convention, e.g., "lower-hyphen". */
  LOWER_HYPHEN(CharMatcher.is('-'), "-") {
    @Override
    String normalizeWord(String word) {
      return word.toLowerCase();
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_UNDERSCORE) {
        return s.replace('-', '_');
      }
      if (format == UPPER_UNDERSCORE) {
        return s.replace('-', '_').toUpperCase();
      }
      return super.convert(format, s);
    }
  },

  /** C++ variable naming convention, e.g., "lower_underscore". */
  LOWER_UNDERSCORE(CharMatcher.is('_'), "_") {
    @Override
    String normalizeWord(String word) {
      return word.toLowerCase();
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_HYPHEN) {
        return s.replace('_', '-');
      }
      if (format == UPPER_UNDERSCORE) {
        return s.toUpperCase();
      }
      return super.convert(format, s);
    }
  },

  /** Java variable naming convention, e.g., "lowerCamel". */
  LOWER_CAMEL(CharMatcher.inRange('A', 'Z'), "") {
    @Override
    String normalizeWord(String word) {
      return firstCharOnlyToUpper(word);
    }

    @Override
    String normalizeFirstWord(String word) {
      return word.toLowerCase();
    }
  },

  /** Java and C++ class naming convention, e.g., "UpperCamel". */
  UPPER_CAMEL(CharMatcher.inRange('A', 'Z'), "") {
    @Override
    String normalizeWord(String word) {
      return firstCharOnlyToUpper(word);
    }
  },

  /** Java and C++ constant naming convention, e.g., "UPPER_UNDERSCORE". */
  UPPER_UNDERSCORE(CharMatcher.is('_'), "_") {
    @Override
    String normalizeWord(String word) {
      return word.toUpperCase();
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_HYPHEN) {
        return s.replace('_', '-').toLowerCase();
      }
      if (format == LOWER_UNDERSCORE) {
        return s.toLowerCase();
      }
      return super.convert(format, s);
    }
  };

  @FunctionalInterface
  private static interface CharMatcher extends Predicate<Character> {
    public static CharMatcher is(char ch) {
      return x -> ch == x;
    }

    public static CharMatcher inRange(char from, char to) {
      return x -> x >= from && x <= to;
    }

    default int indexIn(CharSequence sequence, int start) {
      int length = sequence.length();
      if (start >= length)
        throw new IndexOutOfBoundsException(start);
      for (int i = start; i < length; i++) {
        if (matches(sequence.charAt(i))) {
          return i;
        }
      }
      return -1;
    }

    default boolean matches(char c) {
      return test(c);
    }
  }

  private static interface Converter<A, B> {
    public B doForward(A a);

    public A doBackward(B b);
  }

  private final CharMatcher wordBoundary;
  private final String wordSeparator;

  CaseFormat(CharMatcher wordBoundary, String wordSeparator) {
    this.wordBoundary = wordBoundary;
    this.wordSeparator = wordSeparator;
  }

  /**
   * Converts the specified {@code String str} from this format to the specified {@code format}. A
   * "best effort" approach is taken; if {@code str} does not conform to the assumed format, then
   * the behavior of this method is undefined but we make a reasonable effort at converting anyway.
   */
  public final String to(CaseFormat format, String str) {
    requireNonNull(format);
    requireNonNull(str);
    return (format == this) ? str : convert(format, str);
  }

  /** Enum values can override for performance reasons. */
  String convert(CaseFormat format, String s) {
    // deal with camel conversion
    StringBuilder out = null;
    int i = 0;
    int j = -1;
    while ((j = wordBoundary.indexIn(s, ++j)) != -1) {
      if (i == 0) {
        // include some extra space for separators
        out = new StringBuilder(s.length() + 4 * format.wordSeparator.length());
        out.append(format.normalizeFirstWord(s.substring(i, j)));
      } else {
        requireNonNull(out).append(format.normalizeWord(s.substring(i, j)));
      }
      out.append(format.wordSeparator);
      i = j + wordSeparator.length();
    }
    return (i == 0) ? format.normalizeFirstWord(s)
        : requireNonNull(out).append(format.normalizeWord(s.substring(i))).toString();
  }

  /**
   * Returns a {@code Converter} that converts strings from this format to {@code targetFormat}.
   *
   * @since 16.0
   */
  public Converter<String, String> converterTo(CaseFormat targetFormat) {
    return new StringConverter(this, targetFormat);
  }

  private static final class StringConverter implements Converter<String, String>, Serializable {

    private final CaseFormat sourceFormat;
    private final CaseFormat targetFormat;

    StringConverter(CaseFormat sourceFormat, CaseFormat targetFormat) {
      this.sourceFormat = requireNonNull(sourceFormat);
      this.targetFormat = requireNonNull(targetFormat);
    }

    @Override
    public String doForward(String s) {
      return sourceFormat.to(targetFormat, s);
    }

    @Override
    public String doBackward(String s) {
      return targetFormat.to(sourceFormat, s);
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof StringConverter) {
        StringConverter that = (StringConverter) object;
        return sourceFormat.equals(that.sourceFormat) && targetFormat.equals(that.targetFormat);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return sourceFormat.hashCode() ^ targetFormat.hashCode();
    }

    @Override
    public String toString() {
      return sourceFormat + ".converterTo(" + targetFormat + ")";
    }

    private static final long serialVersionUID = 0L;
  }

  abstract String normalizeWord(String word);

  String normalizeFirstWord(String word) {
    return normalizeWord(word);
  }

  private static String firstCharOnlyToUpper(String word) {
    return word.isEmpty() ? word
        : Character.toUpperCase(word.charAt(0)) + word.substring(1, word.length()).toLowerCase();
  }
}
