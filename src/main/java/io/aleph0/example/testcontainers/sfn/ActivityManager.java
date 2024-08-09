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

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.devvitorfarias.example.testcontainers.sfn.util.Exceptions;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sfn.SfnAsyncClient;
import software.amazon.awssdk.services.sfn.model.ActivityDoesNotExistException;
import software.amazon.awssdk.services.sfn.model.ActivityWorkerLimitExceededException;
import software.amazon.awssdk.services.sfn.model.GetActivityTaskRequest;
import software.amazon.awssdk.services.sfn.model.GetActivityTaskResponse;
import software.amazon.awssdk.services.sfn.model.InvalidArnException;

public class ActivityManager implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ActivityManager.class);

  @FunctionalInterface
  public static interface ActivityTaskHandler {
    public String handleTask(String input) throws IOException;
  }

  public static final Supplier<String> DEFAULT_WORKER_NAME_SUPPLIER =
      () -> Thread.currentThread().getName();

  private final SfnAsyncClient client;
  private final String activityArn;
  private final Supplier<ActivityTaskHandler> handlerSupplier;
  private final Supplier<String> workerNameSupplier;
  private final ExecutorService executor;

  public ActivityManager(SfnAsyncClient client, String activityArn,
      Supplier<ActivityTaskHandler> handlerSupplier) {
    this(client, activityArn, handlerSupplier, DEFAULT_WORKER_NAME_SUPPLIER);
  }

  public ActivityManager(SfnAsyncClient client, String activityArn,
      Supplier<ActivityTaskHandler> handlerSupplier, Supplier<String> workerNameSupplier) {
    this(client, activityArn, handlerSupplier, workerNameSupplier, ForkJoinPool.commonPool());
  }

  public ActivityManager(SfnAsyncClient client, String activityArn,
      Supplier<ActivityTaskHandler> handlerSupplier, Supplier<String> workerNameSupplier,
      ExecutorService executor) {
    this.client = requireNonNull(client);
    this.activityArn = requireNonNull(activityArn);
    this.handlerSupplier = requireNonNull(handlerSupplier);
    this.workerNameSupplier = requireNonNull(workerNameSupplier);
    this.executor = requireNonNull(executor);
  }

  @Override
  public void run() {
    try {
      if (LOGGER.isTraceEnabled())
        LOGGER.debug("ActivityManager {} entering", getActivityArn());
      while (!Thread.interrupted()) {
        if (LOGGER.isTraceEnabled())
          LOGGER.debug("ActivityManager {} waiting for task", getActivityArn());

        // Let folks know we're about to run our activity task
        System.out.println("Thread " + Thread.currentThread());
        synchronized (this) {
          this.notifyAll();
        }

        final String workerName = getWorkerNameSupplier().get();

        final CompletableFuture<GetActivityTaskResponse> responseFuture =
            getClient().getActivityTask(GetActivityTaskRequest.builder()
                .activityArn(getActivityArn()).workerName(workerName).build());

        // Now we're going to check for a task. This is a long poll of 60 seconds.
        GetActivityTaskResponse response;
        try {
          response = responseFuture.get();
        } catch (CompletionException e) {
          if (LOGGER.isWarnEnabled())
            LOGGER.warn("ActivityManager {} interrupted", getActivityArn());
          Throwable cause = e.getCause();
          if (cause instanceof ActivityDoesNotExistException
              || cause instanceof InvalidArnException) {
            // These are not recoverable. We should exit.
            if (LOGGER.isErrorEnabled())
              LOGGER.error("ActivityManager {} does not exist", getActivityArn());
            break;
          }
          if (cause instanceof ActivityWorkerLimitExceededException) {
            // We're over the limit. We should log, and keep trying.
            if (LOGGER.isWarnEnabled())
              LOGGER.warn("ActivityManager {} worker limit exceeded", getActivityArn());
            response = null;
          }
          if (cause instanceof AwsServiceException ase) {
            if (ase.isRetryableException()) {
              // If the exception is retryable, we should log, and keep trying.
              if (LOGGER.isWarnEnabled())
                LOGGER.warn("ActivityManager {} retryable exception", getActivityArn(), ase);
              response = null;
            } else {
              throw (AwsServiceException) ase;
            }
          }
          if (cause instanceof Error error) {
            throw error;
          }
          if (cause instanceof Exception exception) {
            throw exception;
          }
          throw new AssertionError("Unexpected throwable", e);
        } catch (InterruptedException e) {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("ActivityManager {} interrupted", getActivityArn());

          // We were interrupted. We should cancel the task. If the cancellation fails, we should
          // log a warning and move on with our lives.
          boolean cancelled = responseFuture.cancel(true);
          if (!cancelled) {
            if (LOGGER.isWarnEnabled())
              LOGGER.warn("ActivityManager {} could not cancel task", getActivityArn());
          }

          // Re-interrupt our thread, which will cause us to exit the loop.
          Thread.currentThread().interrupt();

          continue;
        }

        // If we didn't get a response, or if we got a response with no task token, then we should
        // keep trying. Otherwise, carry on and handle the task!
        final String taskToken =
            Optional.ofNullable(response).map(GetActivityTaskResponse::taskToken).orElse("");
        if (taskToken.isEmpty()) {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("ActivityManager {} no task available", getActivityArn());
          continue;
        }
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("ActivityManager {} received task {}", getActivityArn(), taskToken);

        // We have a task! Let's handle it.
        final ActivityTaskHandler handler = getHandlerSupplier().get();

        // Do the thing!
        final String input = response.input();
        getExecutor().execute(() -> {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("ActivityManager {} starting task {}", getActivityArn(), taskToken);
          try {
            final String result = handler.handleTask(input);
            if (LOGGER.isDebugEnabled())
              LOGGER.debug("ActivityManager {} completed task {}", getActivityArn(), taskToken);
            getClient().sendTaskSuccess(r -> r.taskToken(taskToken).output(result));
          } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
              LOGGER.error("ActivityManager {} failed task {}", getActivityArn(), taskToken, e);
            getClient().sendTaskFailure(
                r -> r.taskToken(taskToken).cause(Exceptions.printStackTraceToString(e)));
          }
        });

        // Note that we don't wait for the task to finish. We loop immediately.
      }
      if (LOGGER.isInfoEnabled())
        LOGGER.info("ActivityManager {} interrupted, exiting...", getActivityArn());
    } catch (

    Exception e) {
      if (LOGGER.isErrorEnabled())
        LOGGER.error("Error in ActivityManager", e);
    } finally {
      if (LOGGER.isTraceEnabled())
        LOGGER.debug("ActivityManager {} leaving", getActivityArn());
    }
  }

  private SfnAsyncClient getClient() {
    return client;
  }

  private String getActivityArn() {
    return activityArn;
  }

  private Supplier<ActivityTaskHandler> getHandlerSupplier() {
    return handlerSupplier;
  }

  private Supplier<String> getWorkerNameSupplier() {
    return workerNameSupplier;
  }

  private ExecutorService getExecutor() {
    return executor;
  }
}
