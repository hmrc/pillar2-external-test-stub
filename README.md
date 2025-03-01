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
| PUT    | /pillar2/test/organisation/:pillar2Id    | Update existing organisation                   | 200, 400, 404, 500 |
| DELETE | /pillar2/test/organisation/:pillar2Id    | Delete organisation                            | 204, 404, 500 |

#### Response Status Codes:
- 201: Organisation created successfully
- 200: Request completed successfully
- 204: Organisation deleted successfully
- 400: Invalid request (missing/invalid fields)
- 404: Organisation not found
- 409: Organisation already exists
- 500: Internal server error

#### Error Responses
All error responses follow a standard format:
```json
{
  "code": "ERROR_CODE",
  "message": "Human readable error message"
}
```

Error Codes:
- `INVALID_JSON`: Invalid JSON payload provided
- `EMPTY_REQUEST_BODY`: Empty request body provided
- `ORGANISATION_EXISTS`: Organisation with given pillar2Id already exists
- `ORGANISATION_NOT_FOUND`: No organisation found with given pillar2Id
- `DATABASE_ERROR`: Database operation failed

#### Example Requests

##### Create Organisation
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
    "endDate": "2024-12-31"
  }
}'
```

Success Response (201 Created):
```json
{
  "pillar2Id": "XEPLR1234567890",
  "organisation": {
    "orgDetails": {
      "domesticOnly": false,
      "organisationName": "Test Organisation Ltd",
      "registrationDate": "2024-01-01"
    },
    "accountingPeriod": {
      "startDate": "2024-01-01",
      "endDate": "2024-12-31"
    },
    "lastUpdated": "2024-01-01T00:00:00Z"
  }
}
```

Error Response Example (409 Conflict):
```json
{
  "code": "ORGANISATION_EXISTS",
  "message": "Organisation with pillar2Id: XEPLR1234567890 already exists"
}
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
-H "X-Pillar2-Id: XEPLR0000000201" \
-d '{
  "accountingPeriodFrom": "2024-08-14",
  "accountingPeriodTo": "2024-12-14"
}'
```

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

# UK Tax Return (UKTR) Test Suite

## Overview

This repository contains a test suite for validating the UK Tax Return (UKTR) submission functionality. The test suite uses MongoDB to persist test data and curl commands to execute API calls against the service endpoints.

The test suite covers various scenarios including:
- Creating organizations (domestic and non-domestic)
- UKTR submissions (valid and invalid)
- Validation of different error conditions
- Amending existing submissions

## Prerequisites

- MongoDB running locally
- The UKTR service running on port 10055
- `jq` command-line tool for formatting JSON responses

## How to Run Tests

The test suite includes a bash script (`run_tests.sh`) that orchestrates the execution of the tests in the correct order:

```bash
./run_tests.sh
```

Before running the tests, the script will:
1. Drop the MongoDB database to ensure a clean test environment
2. Create test organizations needed for the various test scenarios
3. Execute each test case in sequence
4. Format and display the responses

## Test Sequence and Expected Responses

The tests should be run in the following order to ensure proper test progression:

### 1. Organization Setup

First, test organizations are created:
- A domestic organization with PLR ID `XEPLR1234567891`
- A non-domestic organization with PLR ID `XEPLR1234567892`
- An organization with a different accounting period with PLR ID `XMPLR0012345674`

**Expected Response**: HTTP 201 Created for each organization creation request.

### 2. Valid UKTR Submission

A valid UKTR submission for the domestic organization.

**Expected Response**: HTTP 201 Created with a JSON response containing a submission ID.

**Actual Response**: HTTP 201 Created with a JSON response containing the formBundleNumber and chargeReference.

### 3. Duplicate Submission Test

Attempting to submit the same UKTR data for the same organization and accounting period.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message indicating a duplicate submission.

**Actual Response**: HTTP 422 Unprocessable Entity with an error code "044" and text "A submission already exists for this accounting period".

### 4. Domestic with MTT Test

Attempting to submit a UKTR with the Maximum Top-up Tax (MTT) flag set to true for a domestic-only organization.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message indicating MTT is not allowed for domestic organizations.

**Actual Response**: HTTP 422 Unprocessable Entity with an error code "093" and text "obligationMTT cannot be true for a domestic-only group".

### 5. Subscription Not Found Test

Attempting to submit a UKTR for a non-existent PLR ID.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message indicating the subscription was not found.

**Actual Response**: HTTP 500 Internal Server Error. This needs to be fixed in the controller to return a proper 422 error.

### 6. Amend Submission Test

Attempting to amend a previously submitted UKTR.

**Expected Response**: HTTP 201 Created with a JSON response containing a submission ID.

**Actual Response**: HTTP 201 Created with a JSON response containing the formBundleNumber and chargeReference.

## Additional Test Cases

The following tests are not currently included in the automated test suite but should be implemented:

### No Pillar2Id Test

Attempting to submit a UKTR without providing a Pillar2Id header.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message about the missing header.

### Invalid Accounting Period Test

Attempting to submit a UKTR with an accounting period that doesn't match the organization's registered accounting period.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message about the invalid accounting period.

### Empty Liable Entities Test

Attempting to submit a UKTR with no liable entities.

**Expected Response**: HTTP 422 Unprocessable Entity with an error message indicating liable entities are required.

### Nil Return Amend Test

Attempting to amend a previously submitted UKTR to a nil return.

**Expected Response**: HTTP 201 Created with a JSON response containing a submission ID.

## Known Issues

1. **Subscription Not Found Test**: Currently returns a 500 Internal Server Error instead of the expected 422 Unprocessable Entity. This needs to be fixed in the controller to properly handle the case where a subscription is not found.

2. **Bruno Test Integration**: The current test script uses direct curl commands instead of Bruno tests because of compatibility issues with Bruno CLI 1.0.0. Future versions should integrate properly with Bruno test files.

## Error Handling

The test suite validates proper error handling for various scenarios:

1. **Validation Errors**: The service should return HTTP 422 with appropriate error messages for validation failures.
2. **Not Found Errors**: The service should return HTTP 422 with appropriate error messages when a subscription is not found (currently returns 500).
3. **Duplicate Submission Errors**: The service should return HTTP 422 with appropriate error messages for duplicate submissions.

## Troubleshooting

If tests fail, check the following:

1. Ensure MongoDB is running and accessible
2. Ensure the UKTR service is running on port 10055
3. Check that all prerequisites are installed (`jq`, etc.)
4. Verify that the PLR IDs used in the tests are not already in use in your MongoDB instance

## Reference Data

| Test Case | PLR ID | Organization Type | Accounting Period |
|-----------|--------|-------------------|-------------------|
| Domestic Organization | XEPLR1234567891 | Domestic Only | 2024-01-01 to 2024-03-31 |
| Non-Domestic Organization | XEPLR1234567892 | Non-Domestic | 2024-01-01 to 2024-03-31 |
| Specific Accounting Period | XMPLR0012345674 | Domestic Only | 2024-08-14 to 2024-12-14 |
| Subscription Not Found | XMPLR9999999999 | N/A | N/A |

## Future Improvements

1. Fix the "subscription not found" error handling in the controller to return a 422 status code instead of 500.
2. Update the test script to use Bruno test files directly once compatibility issues are resolved.
3. Add additional test cases for edge conditions and other error scenarios.
4. Create a more comprehensive test suite that includes all Bruno test files.