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
package io.devvitorfarias.example.testcontainers.sfn;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnAsyncClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.CreateActivityRequest;

@Testcontainers
public class ActivityManagerIT {
  /**
   * The LocalStack container is started before any test method is executed and stopped after all
   * test methods are executed.
   * 
   * @see <a href="https://www.testcontainers.org/test_framework_integration/junit_5/">JUnit 5</a>
   * @see <a href="https://docs.localstack.cloud/user-guide/integrations/testcontainers/">LocalStack
   *      Testcontainers</a>
   * @see <a href=
   *      "https://stackoverflow.com/questions/61625288/spring-boot-testcontainers-mapped-port-can-only-be-obtained-after-the-container">Spring
   *      Boot Testcontainers: Mapped port can only be obtained after the container</a>
   */
  @Container
  public static final LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
          // .withEnv("DEBUG", "1").withEnv("LS_LOG", "trace") // Enable debug logging
          .withServices(Service.STEPFUNCTIONS).withLogConsumer(of -> {
            System.err.print(of.getUtf8String());
          });

  public SfnClient sfn;
  public SfnAsyncClient sfnAsync;

  @BeforeEach
  public void setupExampleIT() {
    sfn = SfnClient.builder().endpointOverride(localstack.getEndpoint())
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
        .region(Region.of(localstack.getRegion())).build();
    sfnAsync = SfnAsyncClient.builder().endpointOverride(localstack.getEndpoint())
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
        .region(Region.of(localstack.getRegion()))
        // Override the default timeout of 30 seconds to 65 seconds since we'll be using it for
        // GetActivityTask, per the docs.
        // https://docs.aws.amazon.com/step-functions/latest/apireference/API_GetActivityTask.html
        .overrideConfiguration(
            ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(65))
                .apiCallAttemptTimeout(Duration.ofSeconds(65)).build())
        .build();
  }

  @AfterEach
  public void teardownExampleIT() {
    sfn.close();
    sfnAsync.close();
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  public void givenRunningActivityManager_whenInterrupt_thenStop() throws Exception {
    final String activityName = "InterruptTest";
    final String activityArn = sfn
        .createActivity(CreateActivityRequest.builder().name(activityName).build()).activityArn();

    final ActivityManager manager = new ActivityManager(sfnAsync, activityArn, () -> {
      // We should not live long enough to call this
      throw new UnsupportedOperationException();
    });

    // Create our manager thread
    final Thread thread = new Thread(manager);
    // thread.setDaemon(true); // Up to the developer to decide if this should be daemon or not

    // Wait for the manager to get ready to poll.
    synchronized (manager) {
      thread.start();
      manager.wait();
    }

    // The activity manager will wait for a task, so we should be able to interrupt it. The
    // underlying implementation uses the async client and simply waits for a task, so the interrupt
    // should be caught and the thread should exit pretty quickly.
    Thread.sleep(3000);

    // Interrupt, and wait. We should return fairly quickly.
    thread.interrupt();
    thread.join();
  }
}
