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
package io.devvitorfarias.example.testcontainers.sfn.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import io.aleph0.example.testcontainers.sfn.activity.HelloActivity;

public class HelloActivityTest {
  @Test
  public void givenDefaultSalutation_whenHandleTask_thenUseDefaultSalutation() throws IOException {
    final String name = "Steve";

    final String greeting = new HelloActivity().handleTask("{\"name\":\"%s\"}".formatted(name));

    assertEquals("{\"greeting\":\"%s, %s!\"}".formatted("Hello", name), greeting);
  }

  @Test
  public void givenCustomSalutation_whenHandleTask_thenUseCustomSalutation() throws IOException {
    final String salutation = "Yo";
    final String name = "Steve";

    final String greeting =
        new HelloActivity(salutation).handleTask("{\"name\":\"%s\"}".formatted(name));

    assertEquals("{\"greeting\":\"%s, %s!\"}".formatted(salutation, name), greeting);
  }
}
