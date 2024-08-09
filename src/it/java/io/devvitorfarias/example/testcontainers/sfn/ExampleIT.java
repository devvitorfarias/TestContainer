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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.devvitorfarias.example.testcontainers.sfn.ActivityManager;
import io.devvitorfarias.example.testcontainers.sfn.activity.HelloActivity;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.sfn.SfnAsyncClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionRequest;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionResponse;
import software.amazon.awssdk.services.sfn.model.ExecutionStatus;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

@Testcontainers
public class ExampleIT {
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
          .withServices(Service.CLOUDFORMATION, Service.STEPFUNCTIONS, Service.IAM,
              LocalStackContainer.EnabledService.named("events"))
          .withLogConsumer(of -> {
            System.err.print(of.getUtf8String());
          });

  public CloudFormationClient cfn;
  public SfnClient sfn;
  public SfnAsyncClient sfnAsync;

  @BeforeEach
  public void setupExampleIT() {
    cfn = CloudFormationClient.builder().endpointOverride(localstack.getEndpoint())
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
        .region(Region.of(localstack.getRegion())).build();
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
    cfn.close();
    sfn.close();
    sfnAsync.close();
  }

  @Test
  public void givenLocalStack_whenCallServices_thenServicesRespond() {
    // We don't care about the results. We just want to see if the services are running and we can
    // communicate with them using the clients.
    cfn.listStacks().stackSummaries();
    sfn.listStateMachines().stateMachines();
  }

  @Test
  public void givenLocalStack_whenDeployStack_thenStateMachineExists() throws Exception {
    final String templateBody;
    try (InputStream in = new FileInputStream("cfn-template.yml")) {
      templateBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    final String stackName = "test-stack";

    cfn.createStack(CreateStackRequest
        .builder().stackName(stackName).parameters(Parameter.builder()
            .parameterKey("StateMachineName").parameterValue("TheStateMachine").build())
        .templateBody(templateBody).build());

    Stack stackCreation = null;
    do {
      Thread.sleep(5000);
      stackCreation =
          cfn.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build()).stacks()
              .get(0);
    } while (stackCreation.stackStatus() == StackStatus.CREATE_IN_PROGRESS);

    if (stackCreation.stackStatus() != StackStatus.CREATE_COMPLETE) {
      System.err.println("Stack status reason:");
      System.err.println(stackCreation.stackStatusReason());
      System.err.println();

      System.err.println("Stack events:");
      cfn.describeStackEventsPaginator(
          DescribeStackEventsRequest.builder().stackName(stackName).build()).stackEvents().stream()
          .forEach(System.err::println);
      System.err.println();
    }

    assertEquals(StackStatus.CREATE_COMPLETE, stackCreation.stackStatus());

    final String stateMachineArn = stackCreation.outputs().stream()
        .filter(output -> output.outputKey().equals("StateMachineArn")).map(Output::outputValue)
        .findFirst().orElseThrow(() -> new NoSuchElementException("StateMachineArn"));
    final String activityArn = stackCreation.outputs().stream()
        .filter(output -> output.outputKey().equals("ActivityArn")).map(Output::outputValue)
        .findFirst().orElseThrow(() -> new NoSuchElementException("StateMachineArn"));

    Thread activityThread =
        new Thread(new ActivityManager(sfnAsync, activityArn, HelloActivity::new));
    activityThread.start();
    try {
      StartExecutionResponse startExecutionResponse = sfn.startExecution(StartExecutionRequest
          .builder().stateMachineArn(stateMachineArn).input("{\"name\":\"World\"}").build());
      final String executionArn = startExecutionResponse.executionArn();

      DescribeExecutionResponse describeExecutionResponse;
      do {
        Thread.sleep(5000);
        describeExecutionResponse = sfn.describeExecution(
            DescribeExecutionRequest.builder().executionArn(executionArn).build());
      } while (describeExecutionResponse.status().equals(ExecutionStatus.RUNNING));

      assertEquals(ExecutionStatus.SUCCEEDED, describeExecutionResponse.status());
      assertEquals(describeExecutionResponse.output(), "{\"greeting\":\"Hello, World!\"}");
    } finally {
      activityThread.interrupt();
      activityThread.join();
    }

    cfn.deleteStack(DeleteStackRequest.builder().stackName(stackName).build());

    Stack stackDeletion = null;
    do {
      Thread.sleep(5000);
      try {
        stackDeletion =
            cfn.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build())
                .stacks().get(0);
      } catch (CloudFormationException e) {
        if (e.statusCode() == 400) {
          // Stack does not exist
          stackDeletion = null;
        } else {
          throw e;
        }
      }
    } while (stackDeletion != null
        && stackDeletion.stackStatus() == StackStatus.DELETE_IN_PROGRESS);

    if (stackDeletion != null && stackDeletion.stackStatus() != StackStatus.DELETE_COMPLETE) {
      System.err.println("Stack status reason:");
      System.err.println(stackDeletion.stackStatusReason());
      System.err.println();

      System.err.println("Stack events:");
      cfn.describeStackEventsPaginator(
          DescribeStackEventsRequest.builder().stackName(stackName).build()).stackEvents().stream()
          .forEach(System.err::println);
      System.err.println();
    }
  }
}
