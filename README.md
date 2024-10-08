# java-maven-testcontainers-sfn-example

An [SSCCE](http://www.sscce.org/) <br> 
of using [LocalStack](https://www.localstack.cloud/)  <br> 
via [TestContainers](https://testcontainers.com/)  <br> 
to test a simple [AWS](https://aws.amazon.com/) [Step Function](https://aws.amazon.com/step-functions/) <br> 
in a [Java](https://en.wikipedia.org/wiki/Java_%28programming_language%29)  <br> 
project with [maven](https://maven.apache.org/)  <br> 
build and [JUnit 5 Jupyter](https://junit.org/junit5/docs/current/user-guide/) test framework.

## Building

The build is fully integrated with maven and may be run using simply:

    mvn clean compile test verify

This will build the main code as well as run unit tests via [surefire](https://maven.apache.org/surefire/maven-surefire-plugin/) and integration tests via [failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/).

