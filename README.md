
# pillar2-external-test-stub

The Pillar2 external test stub service provides stubs to mock an ETMP responses.

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

To use test-only route locally, run the below:

`sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'`

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


## Retrieve Subscription

The retrieveSubscription endpoint returns subscription details based on the provided plrReference (PLR Reference Number). Different plrReference values yield different responses to simulate various scenarios, including successful responses with domestic or non-domestic status and specific error responses.

Response Codes and Conditions

| plrReference                 | HTTP Status               | Description                                                                    |
|:-----------------------------|---------------------------|--------------------------------------------------------------------------------|
| XEPLR0123456400              | 400 Bad Request           | Invalid request due to a correlation ID error.                                 |
| XEPLR0123456404              | 404 Not Found             | Subscription not found.                                                        |
| XEPLR0123456422              | 422 Unprocessable Entity  | Duplicate record - request cannot be processed.                                |
| XEPLR0123456500              | 500 Internal Server Error | Server error.                                                                  |
| XEPLR0123456503              | 503 Service Unavailable   | Dependent systems are currently not responding.                                |
| XEPLR5555555555              | 200 OK                    | Success response with domesticOnly = true.                                     |
| XEPLR1234567890              | 200 OK                    | Success response with domesticOnly = false.                                    |                                               
| Any other valid plrReference | 404 Not Found             | Subscription not found (default response for unspecified plrReference values). |                                               
| XEPLR0987654321              | 200 OK                    | Success response for a Nil Return.                                             |                                               

# Curl Call Examples

Use the following curl commands to test different responses for the retrieveSubscription endpoint. Replace valid_token with an appropriate authorization token if required.

1. Bad Request (Invalid Correlation ID) - plrReference XEPLR0123456400
     ```
      curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0123456400" \
      -H "Authorization: Bearer valid_token" \
      -H "Content-Type: application/json"
      ```
2. Not Found (Subscription Not Found) - plrReference XEPLR0123456404
    ```
    curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0123456404" \
    -H "Authorization: Bearer valid_token" \
    -H "Content-Type: application/json"
    ```
3. Unprocessable Entity (Duplicate Record) - plrReference XEPLR0123456422
    ```
    curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0123456422" \
      -H "Authorization: Bearer valid_token" \
      -H "Content-Type: application/json"
    ```
4. Internal Server Error - plrReference XEPLR0123456500
    ```
    curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0123456500" \
      -H "Authorization: Bearer valid_token" \
      -H "Content-Type: application/json"
    ```
5. Service Unavailable - plrReference XEPLR0123456503
    ```
   curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0123456503" \
   -H "Authorization: Bearer valid_token" \
   -H "Content-Type: application/json"
    ```
6. OK with domesticOnly = true - plrReference XEPLR5555555555
    ```
   curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR5555555555" \
   -H "Authorization: Bearer valid_token" \
   -H "Content-Type: application/json"
    ```
7. OK with domesticOnly = false - plrReference XEPLR1234567890
    ```
   curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR1234567890" \
   -H "Authorization: Bearer valid_token" \
   -H "Content-Type: application/json"
    ```

8. OK Nil Return - plrReference XEPLR0987654321
    ```
   curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR0987654321" \
   -H "Authorization: Bearer valid_token" \
   -H "Content-Type: application/json"
    ```
   
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").