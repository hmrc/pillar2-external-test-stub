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

## System Overview

### Persistence & Interactions
This service maintains a persistent state using MongoDB to simulate a real-world environment. Understanding the data model is crucial for effective testing:

1.  **Organisation First**: Before any submissions (UKTR, BTN, ORN, GIR) can be made, an **Organisation** must be created using the Organisation Management endpoints. The `pillar2Id` serves as the primary key linking an organisation to its submissions.
2.  **Validation**: Submissions are validated against the stored Organisation data. Examples include:
    *   **Accounting Period**: The `accountingPeriod` in a submission **must match** the `accountingPeriod` defined for the Organisation.
    *   **Under Enquiry**: Some submissions (e.g., BTN) may be rejected if the organisation's accounting period is marked as "under enquiry".
    *   **Organisation Existence**: Submissions will fail if the Organisation does not exist.
3.  **State Updates**: Submissions can alter the state of an Organisation:
    *   **UK Tax Return (UKTR)**: Submitting a UKTR sets the organisation status to **Active**.
    *   **Below Threshold Notification (BTN)**: Submitting a BTN sets the organisation status to **Inactive**.
4.  **Obligations & Submissions History**: All successful submissions (UKTR, BTN, ORN, GIR) are aggregated into an "Obligations and Submissions" history. This allows the `GET /RESTAdapter/plr/obligations-and-submissions` endpoint to provide a consolidated view of a customer's compliance history for a given date range.

## API Reference

### 1. Organisation Management
**Prerequisite**: You must create an organisation before testing other endpoints.

| Method | Endpoint | Description |
|:---|:---|:---|
| POST | `/pillar2/test/organisation/:pillar2Id` | Create a new organisation |
| GET | `/pillar2/test/organisation/:pillar2Id` | Retrieve organisation details |
| PUT | `/pillar2/test/organisation/:pillar2Id` | Update existing organisation |
| DELETE | `/pillar2/test/organisation/:pillar2Id` | Delete organisation |

### 2. Subscription
| Method | Endpoint | Description |
|:---|:---|:---|
| GET | `/pillar2/subscription/:plrReference` | Retrieve subscription details |

**Note**: This endpoint is **not connected to the persistent state**. It is primarily used to simulate subscription retrieval. Since actual subscription cannot be simulated here, it acts as a passthrough in most cases.

**Simulated Responses based on `plrReference`**:
- `XEPLR5555555554`: 404 Not Found
- `XEPLR0123456500`: 500 Internal Server Error
- `XEPLR0123456503`: 503 Service Unavailable
- `XEPLR1234567890`: Success (Not Domestic Only)
- **Any other ID**: Success (Domestic Only) - *Wildcard response*

### 3. UK Tax Return (UKTR)
| Method | Endpoint | Description |
|:---|:---|:---|
| POST | `/RESTAdapter/plr/uk-tax-return` | Submit a UK Tax Return |
| PUT | `/RESTAdapter/plr/uk-tax-return` | Amend a UK Tax Return |

**Note**: Validates against the Organisation's accounting period.

### 4. Below Threshold Notification (BTN)
| Method | Endpoint | Description |
|:---|:---|:---|
| POST | `/RESTAdapter/plr/below-threshold-notification` | Submit a BTN |

**Note**: Validates against the Organisation's accounting period.

### 5. Overseas Return Notification (ORN)
| Method | Endpoint | Description |
|:---|:---|:---|
| POST | `/RESTAdapter/plr/overseas-return-notification` | Submit an ORN |
| PUT | `/RESTAdapter/plr/overseas-return-notification` | Amend an ORN |
| GET | `/RESTAdapter/plr/overseas-return-notification` | Retrieve ORN details |

**Note**: Validates against the Organisation's accounting period.

### 6. Globe Information Return (GIR)
| Method | Endpoint | Description |
|:---|:---|:---|
| POST | `/pillar2/test/globe-information-return` | Submit a GIR |

**Note**: Validates against the Organisation's accounting period.

### 7. Obligations and Submissions
| Method | Endpoint | Description |
|:---|:---|:---|
| GET | `/RESTAdapter/plr/obligations-and-submissions` | Retrieve history of obligations and submissions |

**Parameters**:
- `fromDate`: Start date (YYYY-MM-DD)
- `toDate`: End date (YYYY-MM-DD)

### 8. Account Activity
| Method | Endpoint                                                   | Description                                
|:---|:-----------------------------------------------------------|:-------------------------------------------|
| GET | `/pillar2id/test/organisation/account-activity/:pillar2Id` | Retrieve Account Activity Scenario Details |

## Example Requests

### Create Organisation (Required First Step)
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

### Retrieve Subscription
```bash
curl -X GET "http://localhost:10055/pillar2/subscription/XEPLR5555555555" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json"
```

### Submit UKTR
```bash
curl -X POST "http://localhost:10055/RESTAdapter/plr/uk-tax-return" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-H "X-Pillar2-Id: XEPLR1234567890" \
-d '{
  "accountingPeriodFrom": "2024-01-01",
  "accountingPeriodTo": "2024-12-31",
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

### Submit Below Threshold Notification (BTN)
```bash
curl -X POST "http://localhost:10055/RESTAdapter/plr/below-threshold-notification" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-H "X-Pillar2-Id: XEPLR1234567890" \
-d '{
  "accountingPeriodFrom": "2024-01-01",
  "accountingPeriodTo": "2024-12-31"
}'
```

### Retrieve Obligations and Submissions
```bash
curl -X GET "http://localhost:10055/RESTAdapter/plr/obligations-and-submissions?fromDate=2024-01-01&toDate=2024-12-31" \
-H "Authorization: Bearer valid_token" \
-H "Content-Type: application/json" \
-H "X-Pillar2-Id: XEPLR1234567890"
```

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).