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

## Available Endpoints

### 1. Retrieve Subscription

The retrieveSubscription endpoint returns subscription 
details based on the provided plrReference (PLR 
Reference Number). Different plrReference values yield 
different responses to simulate various scenarios, 
including successful responses with domestic or 
non-domestic status and specific error responses.

Response Codes and Conditions:

| plrReference                 | HTTP Status               | Description                                                                    |
|:-----------------------------|---------------------------|--------------------------------------------------------------------------------|
| XEPLR0123456404              | 404 Not Found             | Subscription not found                                                         |
| XEPLR0123456500              | 500 Internal Server Error | Server error                                                                  |
| XEPLR0123456503              | 503 Service Unavailable   | Dependent systems are currently not responding                                |
| XEPLR5555555555              | 200 OK                    | Success response with domesticOnly = true                                     |
| XEPLR1234567890              | 200 OK                    | Success response with domesticOnly = false                                    |
| XEPLR0987654321              | 200 OK                    | Success response for a Nil Return                                             |

### 2. Submit UKTR

The /pillar2/submitUKTR/:plrReference endpoint submits a UKTR to ETMP.

Response Codes and Conditions:

| plrReference      | HTTP Status               | Description                                                 |
|:-----------------|---------------------------|-------------------------------------------------------------|
| XEPLR0000000422  | 422 Unprocessable Entity | Business validation failure with error details               |
| XEPLR0000000500  | 500 Internal Server Error| SAP system failure with error details                       |
| XEPLR0000000400  | 400 Bad Request         | Invalid JSON payload error                                   |
| Any other        | 201 Created             | Successful UKTR submission with form bundle details          |

## Example Requests

### Retrieve Subscription Example
```bash
curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR5555555555" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json"
```

### Submit UKTR Request Example
```bash
curl -X POST "http://localhost:10055/pillar2/submitUKTR/XEPLR0000000422" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-d '{
  "accountingPeriodFrom": "2024-08-14",
  "accountingPeriodTo": "2024-12-14",
  "obligationMTT": true,
  "electionUKGAAP": true,
  "liabilities": {
    "electionDTTSingleMember": false,
    "electionUTPRSingleMember": false,
    "numberSubGroupDTT": 4,
    "numberSubGroupUTPR": 5,
    "totalLiability": 10000.99,
    "totalLiabilityDTT": 5000.99,
    "totalLiabilityIIR": 4000,
    "totalLiabilityUTPR": 10000.99,
    "liableEntities": [
      {
        "ukChargeableEntityName": "UKTR Newco PLC",
        "idType": "CRN",
        "idValue": "12345678",
        "amountOwedDTT": 5000,
        "electedDTT": true,
        "amountOwedIIR": 3400,
        "amountOwedUTPR": 6000.5,
        "electedUTPR": true
      }
    ]
  }
}'
```

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).