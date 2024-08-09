/*-
 * =================================LICENSE_START==================================
 * java-maven-testcontainers-sfn-example
 * ====================================SECTION=====================================
 * Copyright (C) 2024 devvitorfarias
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
package io.devvitorfarias.example.testcontainers.sfn.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;

public final class Exceptions {
  private Exceptions() {}

  /**
   * Convert the stack trace of an exception to a string.
   * 
   * @param e the exception
   * @return the stack trace as a string
   */
  public static String printStackTraceToString(Exception e) {
    try (StringWriter sw = new java.io.StringWriter();
        PrintWriter pw = new java.io.PrintWriter(sw)) {
      e.printStackTrace(pw);
      return sw.toString();
    } catch (IOException x) {
      // This should never happen, since all our I/O is all in memory.
      throw new UncheckedIOException(x);
    }
  }
}
