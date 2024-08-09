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

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.devvitorfarias.example.testcontainers.sfn.ActivityManager;

public class HelloActivity implements ActivityManager.ActivityTaskHandler {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static record Request(String name) {
  }

  public static record Response(String greeting) {
  }

  public static final String DEFAULT_SALUTATION = "Hello";

  private final String salutation;

  public HelloActivity() {
    this(DEFAULT_SALUTATION);
  }

  public HelloActivity(String salutation) {
    this.salutation = salutation;
  }

  @Override
  public String handleTask(String input) throws IOException {
    final Request request = MAPPER.readValue(input, Request.class);

    final String name = request.name();
    final String greeting = "%s, %s!".formatted(getSalutation(), name);

    final Response response = new Response(greeting);

    return MAPPER.writeValueAsString(response);
  }

  private String getSalutation() {
    return salutation;
  }
}
