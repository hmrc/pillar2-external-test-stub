## Stub Behaviour

### Introduction
This document intends to document how this stub behaves in relation to data created and how that will impact
data subsequently returned from other endpoints. It also covers specific validation scenarios that could 
happen when specific sequences of submissions are made.

#### Dynamic Stubbed Behaviour
This stub is dynamic. If you submit a UKTR and amend it thrice, you will get four entries returned in the submission history endpoint.
Creating this dynamic behaviour is intended to replicate our production downstream dependencies.

### Key Definitions
**Disclaimer:** These definitions are intended to help understand stubbed behaviour. They are not intended as key definitions for Pillar 2.
For those definitions, please see official guidance on [Gov.uk](https://www.gov.uk/government/collections/multinational-top-up-tax-and-domestic-top-up-tax).

#### Accounting Period
The pre-defined accounting period for a multinational or domestic organisation. Each accounting period will
have specific obligations that must be satisfied with submissions of pre-determined type.

When creating a Test Organisation, the accounting period can be defined as a body parameter.

#### Obligation
An obligation defines the submissions that organisations are required to make for each accounting period. An obligation is
fulfilled by a submission. 
Before a submission is made to an obligation, the obligation can be said to be "Due". If a submission is not made before the due date, it can be said to be "Overdue".

Obligations can be fetched for each accounting period by using the GET Obligations and Submissions endpoint.

The two obligations relevant to Pillar 2 are:
1. Pillar2TaxReturn
2. GlobeInformationReturn

#### Submissions
There are four different types of submission, three of which can be made through this stub:
1. UKTaxReturn
   2. Satisfies a Pillar2TaxReturn obligation
2. Below-Threshold Notification
   3. Determines the organisation as below the threshold for Pillar 2, therefore satisfies all obligations until superseded
3. Overseas Return Notification
   4. Satisfies a GlobeInformationReturn obligation

The other type of submission is a GIR, but this cannot be submitted via this stub.

##### Below-Threshold Notification
A Below-Threshold notification is used to notify HMRC that the organisation's consolidated annual revenues have fallen below the Pillar 2 threshold of €750 million.

By submitting a BTN, all obligations are satisfied for that accounting period and all subsequent accounting periods until a UKTR is submitted.


### Using These Stubs
Before using these stubs, you will need to [create a Test User](https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/api-platform-test-user/1.0).

Once the Test User is created, it is generally recommended to interact with these stubs on a scenario basis, by creating a test organisation, using the API and then deleting the test organisation.

If you do not delete the test organisation, it will be deleted within 28 days.

### Test Organisation
[![](https://mermaid.ink/img/pako:eNp1UsFOwzAM_ZUoV1YVOPYwCYkLB7RJHRfUi5eYNVqbhMQRQtP-Hacpo5tETs7z83t2nJNUTqNsZGcjfia0Cp8NHAKMnRV8QJELQg0GLRXEQyCjjAdLAhL1BS2Mar2-y1gjtpt2J2oH1NfkjmhnNc5lTmE3ArwfjAIyzoo9QsAgFmwVEAivDJOAKAgjR5HJT9uXW_v0612qq0yuMrl24QDWxMktlrK0bGaXZd-yrEYCM8R_u2gp7eOt8QReBl961d4MA4SKvlwdkZK_ShedqXrZzeP9w2ytOytXcsQwgtG8qVOu6CT1OGInGw41hGPHGzwzj5_Ytd9WyYZCwpUMLh162XzAEPmWvGbFecMXlId6d-7vjtrw1l_Lx5j-x_kH_p69Fw?type=png)](https://mermaid.live/edit#pako:eNp1UsFOwzAM_ZUoV1YVOPYwCYkLB7RJHRfUi5eYNVqbhMQRQtP-Hacpo5tETs7z83t2nJNUTqNsZGcjfia0Cp8NHAKMnRV8QJELQg0GLRXEQyCjjAdLAhL1BS2Mar2-y1gjtpt2J2oH1NfkjmhnNc5lTmE3ArwfjAIyzoo9QsAgFmwVEAivDJOAKAgjR5HJT9uXW_v0612qq0yuMrl24QDWxMktlrK0bGaXZd-yrEYCM8R_u2gp7eOt8QReBl961d4MA4SKvlwdkZK_ShedqXrZzeP9w2ytOytXcsQwgtG8qVOu6CT1OGInGw41hGPHGzwzj5_Ytd9WyYZCwpUMLh162XzAEPmWvGbFecMXlId6d-7vjtrw1l_Lx5j-x_kH_p69Fw)

The test organisation is created to facilitate storage of submissions against a specific accounting period. This data is then used to fulfill any GET requests made on the API.


```shell
curl --request POST \
  --url http://test-api.service.hmrc.gov.uk/organisations/pillar-two/setup/organisation \
  --header 'accept: application/vnd.hmrc.1.0+json' \
  --header 'authorization: Bearer {{bearer_token}}' \
  --header 'content-type: application/json' \
  --data '{
  "orgDetails": {
    "domesticOnly": true,
    "organisationName": "Test Organisation Ltd",
    "registrationDate": "2025-03-01"
  },
  "accountingPeriod": {
    "startDate": "2024-01-01",
    "endDate": "2024-12-31"
  }
}'
```

This cURL will create a test organisation that is **domesticOnly** and has a specific accounting period. 

The **domesticOnly** flag is used to create a MNE or UK-only organisation. This will be important when testing the submitUKTR endpoint and some of the conditional flags that depend on this value.

### Scenario 1: Submit UKTaxReturn
[![](https://mermaid.ink/img/pako:eNp1kbtOAzEQRX9lNC2xCCldpAJRoSAtVeRm1p5srKzHwQ8JFOXf8e4CgiLdjH3ulXV8QRsdo0allBEb5eAHbQSgHDmwBuGoHKWTkRnI_F5ZLD96GhKFCQQgW2ICO3qWspwss9pu77pS-6zhdde9wX09qUIfKnGpSRZyvp_AJaJhs34Am5gKuxtdz0-tKvajH6j4KJnE5doHn_O03axdw58MtBD8S-EKA6dA3jUZl6nF4OzAoG7j7ACNXBtHtcTuUyzqkiqvMMU6HFEfaMxtq2fXHv8t6Ac5k-xjDL8QO9-cvSzq5x-4fgH6coWA?type=png)](https://mermaid.live/edit#pako:eNp1kbtOAzEQRX9lNC2xCCldpAJRoSAtVeRm1p5srKzHwQ8JFOXf8e4CgiLdjH3ulXV8QRsdo0allBEb5eAHbQSgHDmwBuGoHKWTkRnI_F5ZLD96GhKFCQQgW2ICO3qWspwss9pu77pS-6zhdde9wX09qUIfKnGpSRZyvp_AJaJhs34Am5gKuxtdz0-tKvajH6j4KJnE5doHn_O03axdw58MtBD8S-EKA6dA3jUZl6nF4OzAoG7j7ACNXBtHtcTuUyzqkiqvMMU6HFEfaMxtq2fXHv8t6Ac5k-xjDL8QO9-cvSzq5x-4fgH6coWA)


The first, simple solution is to submit a UK Tax Return that satisfies the Pillar2TaxReturn obligation.

First, we can check the obligations for this organisation:
```shell
curl --request GET \
  --url http://test-api.service.hmrc.gov.uk/organisations/pillar-two/submissionandobligation?fromDate=2024-01-01&toDate=2024-12-31 \
  --header 'accept: application/vnd.hmrc.1.0+json' \
  --header 'authorization: Bearer {{bearer_token}}' 
```

This will return the obligations for all accountingPeriods that occur during the requested date range:
```json
{
  "success": {
    "processingDate": "2025-01-01T09:26:17Z",
    "accountingPeriodDetails": [
      {
        "startDate": "2024-01-01",
        "endDate": "2024-12-31",
        "dueDate": "2025-01-31",
        "underEnquiry": false,
        "obligations": [
          {
            "obligationType": "Pillar2TaxReturn",
            "status": "Open",
            "canAmend": true,
            "submissions": []
          },
          {
            "obligationType": "GlobeInformationReturn",
            "status": "Open",
            "canAmend": true,
            "submissions": []
          }
        ]
      }
    ]
  }
}
```

From this response, we can see that for our accounting period, we have two obligations which are open and due. We can satisfy one of these obligations by submitting a UK Tax Return.

```shell
curl --request POST \
  --url http://test-api.service.hmrc.gov.uk/organisations/pillar-two/uk-tax-return \
  --header 'accept: application/vnd.hmrc.1.0+json' \
  --header 'authorization: Bearer {{bearer_token}}' \
  --header 'content-type: application/json' \
  --data '{
  "accountingPeriodFrom": "2024-01-01",
  "accountingPeriodTo": "2024-12-31",
  "obligationMTT": false,
  "electionUKGAAP": true,
  "liabilities": {
    "electionDTTSingleMember": false,
    "electionUTPRSingleMember": false,
    "numberSubGroupDTT": 1,
    "numberSubGroupUTPR": 1,
    "totalLiability": 5000,
    "totalLiabilityDTT": 5000,
    "totalLiabilityIIR": 0,
    "totalLiabilityUTPR": 0,
    "liableEntities": [
      {
        "ukChargeableEntityName": "Newco PLC",
        "idType": "CRN",
        "idValue": "12345678",
        "amountOwedDTT": 5000,
        "amountOwedIIR": 0,
        "amountOwedUTPR": 0
      }
    ]
  }
}'
```

and then a subsequent call to the Submissions and Obligations endpoint will show us this submission and the fulfilled obligation.

```json
{
  "success": {
    "processingDate": "2025-03-17T09:26:17Z",
    "accountingPeriodDetails": [
      {
        "startDate": "2024-01-01",
        "endDate": "2024-12-31",
        "dueDate": "2025-01-31",
        "underEnquiry": false,
        "obligations": [
          {
            "obligationType": "Pillar2TaxReturn",
            "status": "Fulfilled",
            "canAmend": true,
            "submissions": [
              {
                "submissionType": "UKTR",
                "receivedDate": "2025-03-17T09:26:17Z"
              }
            ]
          },
          {
            "obligationType": "GlobeInformationReturn",
            "status": "Open",
            "canAmend": true,
            "submissions": []
          }
        ]
      }
    ]
  }
}
```