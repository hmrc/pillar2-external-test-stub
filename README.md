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

| plrReference            | HTTP Status               | Description                                     |
|:------------------------|---------------------------|-------------------------------------------------|
| XEPLR0123456500         | 500 Internal Server Error | Server error                                    |
| XEPLR0123456503         | 503 Service Unavailable   | Dependent systems are currently not responding  |
| XEPLR5555555555         | 200 OK                    | Success response with domesticOnly = true       |
| XEPLR1234567890         | 200 OK                    | Success response with domesticOnly = false      |
| Any other               | 404 Not Found             | Subscription not found                          |

### 2. Submit UKTR

Endpoint: /RESTAdapter/PLR/UKTaxReturn

Response Codes and Conditions:

| plrReference    | HTTP Status               | Description                                         |
|:----------------|---------------------------|-----------------------------------------------------|
| XEPLR0000000422 | 422 Unprocessable Entity  | Business validation failure with error details      |
| XEPLR0000000500 | 500 Internal Server Error | SAP system failure with error details               |
| XEPLR0000000400 | 400 Bad Request           | Invalid JSON payload error                          |
| Any other       | 201 Created               | Successful UKTR submission with form bundle details |

### 3. Amend UKTR (Placeholder)

### 4. Submit Below Threshold Notification (BTN)

Endpoint: /RESTAdapter/PLR/below-threshold-notification

Response Codes and Conditions:

| plrReference    | HTTP Status               | Description                                    |
|:----------------|---------------------------|------------------------------------------------|
| XEPLR0000000201 | 201 Created               | Business validation failure with error details |
| XEPLR0000000400 | 400 Bad Request           | Invalid JSON payload error                     |
| XEPLR0000000422 | 422 Unprocessable Entity  | Business validation failure with error details |
| XEPLR0000000500 | 500 Internal Server Error | SAP system failure: ...                        |
| Any other       | (validation-dependent)    | (validation-dependent)                         |

### 5. Organisation Management

Endpoints for managing test organisation data:

| Method | Endpoint                            | Description                                    | Response Codes |
|:-------|-------------------------------------|------------------------------------------------|---------------|
| POST   | /pillar2/test/organisation/:pillar2Id    | Create a new organisation                      | 201, 400, 409, 500 |
| GET    | /pillar2/test/organisation/:pillar2Id    | Retrieve organisation details                  | 200, 404 |
| PUT    | /pillar2/test/organisation/:pillar2Id    | Update existing organisation                   | 200, 400, 500 |
| DELETE | /pillar2/test/organisation/:pillar2Id    | Delete organisation                            | 204, 404, 500 |

Response Status Codes:
- 201: Organisation created successfully
- 200: Request completed successfully
- 204: Organisation deleted successfully
- 400: Invalid request (missing/invalid fields)
- 404: Organisation not found
- 409: Organisation already exists
- 500: Internal server error

Example Request:
```bash
curl -X POST "http://localhost:10055/pillar2/test/organisation/XEPLR1234567890" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-d '{
  "orgDetails": {
    "domesticOnly": false,
    "organisationName": "Test Organisation Ltd",
    "registrationDate": "2024-01-01"
  },
  "accountingPeriod": {
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "dueDate": "2024-04-06"
  }
}'
```

## Example Requests

### Retrieve Subscription Example
```bash
curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR5555555555" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json"
```

### Submit UKTR Request Example
```bash
curl -X POST "http://localhost:10055/RESTAdapter/PLR/UKTaxReturn" \
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


### Submit Below Threshold Notification (BTN) Request Example
```bash
curl -X POST "http://localhost:10055/RESTAdapter/PLR/below-threshold-notification" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-H "X-Pillar2-Id: XEPLR0000000201" 
-d '{
  "accountingPeriodFrom": "2024-08-14",
  "accountingPeriodTo": "2024-12-14"
  }
}'
```

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).