
# pillar2-external-test-stub

The Pillar2 external test stubbs service provides stubbs to mock ETMP responses.

## Running the service locally

#### To compile the project:

`sbt clean update compile`

#### To check code coverage:

`sbt scalafmt test:scalafmt it:test::scalafmt coverage test it/test coverageReport`

#### Integration and unit tests

To run the unit tests within the project:

`sbt test`

#### Starting the server in local

`sbt run`

By default, the service runs locally on port **10052**

To use test-only route locally, run the below:

`sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes 10055'`

### Using Service Manager

You can use service manager to provide necessary assets to the pillar2 backend.
**PILLAR2_ALL** service is responsible for starting up all the services required by the tax credits service project.

This can be started by running the below in a new terminal:

    sm2 --start PILLAR2_ALL

#### Using sbt

For local development, use `sbt run` but if it is already running in sm2, execute below command to stop the
service before running sbt commands.

    sm2 --stop PILLAR_2_EXTERNAL_TEST_STUB

This is an authenticated service, so users first need to be authenticated via GG in order to use the service.

Navigate to http://localhost:9949/auth-login-stub/gg-sign-in which redirects to auth-login-stub page.

Make sure to fill in the fields as below:

***Redirect URL: http://localhost:10050/report-pillar2-top-up-taxes***

***Affinity Group: Organisation***

## Retrieve Subscription

The retrieveSubscription endpoint returns subscription details based on the provided plrReference (PLR Reference Number). Different plrReference values yield different responses to simulate various scenarios.

Response Codes and Conditions

| Status                                       |Description                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| XEPLR4000000000                                                          | Invalid request due to a correlation ID error.                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| XEPLR4040000000                                                          | NOT_FOUND Error Response                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| XEPLR5000000000                                                          | SERVER_ERROR Error Response                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| XEPLR5030000000                                                          | SERVICE_UNAVAILABLE Error Response                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| XMPLR0000000(Last three digits are the number of transactions displayed) | For example <br/> - XMPLR0000000022 will display 22 transactions 12 refund and 12 payments. <br/> - XMPLR0000000122 will display 122 transactions 61 refunds and 61 payments <br/> **Please note** <br/> - Use even numbers since 13 will default to 6 refund and 6 payment <br/> - All returned values are randomised so figures won't be consistent <br/> Please note a user must be able to see only last 7 years of transactions on their account, to test read note below  |
| Any valid ID                                                             | Will return 10 transactions these values are consistent                                                                                                                                                                                                                                                                                                                                                                                                                         |
| XEPLR5555551111                                                          | Will return success response with registration date set to the current date                                                                                                                                                                                                                                                                                                                                                                                                     |                                               


This is a placeholder README.md for a new repository

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").