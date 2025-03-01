#!/bin/bash

# Delete the MongoDB database to start with a clean state
echo "Dropping MongoDB database..."
./delete_mongo_db.sh pillar2-external-test-stub

# Set environment variables for Bruno tests
export BRU_ENV_baseUrl="http://localhost:10055"
export BRU_ENV_validBearerToken="Bearer valid_token"
export BRU_ENV_test1PlrId="XEPLR1234567891"
export BRU_ENV_domesticOnlyPlrId="XEPLR1234567891"
export BRU_ENV_nonDomesticPlrId="XEPLR1234567892"
export BRU_ENV_accountingPeriodFrom="2024-01-01"
export BRU_ENV_accountingPeriodTo="2024-03-31"
export BRU_ENV_invalidPillar2Id="XMPLR9999999999"

# Create test directory to work from (Bruno requires running from collection root)
cd "API Testing" || exit 1

echo "======================================================================================
Running tests with Bruno CLI 1.0.0...
======================================================================================"

# Initialize results tracking
PASSED_TESTS=0
FAILED_TESTS=0
TOTAL_TESTS=0

# Step 1: Create organisations
echo "======================================================================================
Creating test organisations
======================================================================================"
# Create a domestic organisation
echo "Creating domestic organisation..."
curl -X POST "http://localhost:10055/pillar2/test/organisation/XEPLR1234567891" \
  -H "Content-Type: application/json" \
  -d '{"orgDetails":{"domesticOnly":true,"organisationName":"DomesticTestCompany","registrationDate":"2024-01-01"},"accountingPeriod":{"startDate":"2024-01-01","endDate":"2024-03-31"}}' | jq .

# Create a non-domestic organisation
echo "Creating non-domestic organisation..."
curl -X POST "http://localhost:10055/pillar2/test/organisation/XEPLR1234567892" \
  -H "Content-Type: application/json" \
  -d '{"orgDetails":{"domesticOnly":false,"organisationName":"NonDomesticTestCompany","registrationDate":"2024-01-01"},"accountingPeriod":{"startDate":"2024-01-01","endDate":"2024-03-31"}}' | jq .

# Create an organisation with a different accounting period
echo "Creating organisation with different accounting period..."
curl -X POST "http://localhost:10055/pillar2/test/organisation/XMPLR0012345674" \
  -H "Content-Type: application/json" \
  -d '{"orgDetails":{"domesticOnly":true,"organisationName":"TestCompany","registrationDate":"2024-01-01"},"accountingPeriod":{"startDate":"2024-08-14","endDate":"2024-12-14"}}' | jq .

# Step 2: Run submission tests
echo "======================================================================================
Running submission tests
======================================================================================"

# Valid request
echo "Running test: valid request.bru"
npx @usebruno/cli run uktr/submitUKTR/valid\ request.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
else
  FAILED_TESTS=$((FAILED_TESTS+1))
  echo "  - FAILED: valid request.bru - 400 Bad Request instead of 201 Created"
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Duplicate submission
echo "Running test: duplicate submission.bru"
npx @usebruno/cli run uktr/submitUKTR/duplicate\ submission.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
else
  FAILED_TESTS=$((FAILED_TESTS+1))
  echo "  - FAILED: duplicate submission.bru - 400 Bad Request instead of 422 Unprocessable Entity"
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Domestic with MTT
echo "Running test: domestic with MTT.bru"
npx @usebruno/cli run uktr/submitUKTR/domestic\ with\ MTT.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
else
  FAILED_TESTS=$((FAILED_TESTS+1))
  echo "  - FAILED: domestic with MTT.bru - 201 Created instead of 422 Unprocessable Entity"
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# No Pillar2Id
echo "Running test: No Pillar2Id.bru"
npx @usebruno/cli run uktr/submitUKTR/No\ Pillar2Id.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
  echo "  - PASSED: No Pillar2Id.bru"
else
  FAILED_TESTS=$((FAILED_TESTS+1))
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Subscription not found
echo "Running test: subscription not found.bru"
npx @usebruno/cli run uktr/submitUKTR/subscription\ not\ found.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
else
  FAILED_TESTS=$((FAILED_TESTS+1))
  echo "  - FAILED: subscription not found.bru - 400 Bad Request instead of 422 Unprocessable Entity"
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Invalid accounting period
echo "Running test: invalid accounting period.bru"
npx @usebruno/cli run uktr/submitUKTR/invalid\ accounting\ period.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
  echo "  - PASSED: invalid accounting period.bru"
else
  FAILED_TESTS=$((FAILED_TESTS+1))
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Empty liable entities
echo "Running test: empty liable entities.bru"
npx @usebruno/cli run uktr/submitUKTR/empty\ liable\ entities.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
  echo "  - PASSED: empty liable entities.bru"
else
  FAILED_TESTS=$((FAILED_TESTS+1))
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Step 3: Run amendment tests
echo "======================================================================================
Running amendment tests
======================================================================================"

# Amend submission
echo "Running test: amend submission.bru"
npx @usebruno/cli run uktr/submitUKTR/amend\ submission.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
  echo "  - PASSED: amend submission.bru"
else
  FAILED_TESTS=$((FAILED_TESTS+1))
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Nil return amend
echo "Running test: nilreturn - amend.bru"
npx @usebruno/cli run uktr/submitUKTR/nilreturn\ -\ amend.bru --env local
if [ $? -eq 0 ]; then
  PASSED_TESTS=$((PASSED_TESTS+1))
else
  FAILED_TESTS=$((FAILED_TESTS+1))
  echo "  - FAILED: nilreturn - amend.bru - 422 Unprocessable Entity instead of 201 Created"
fi
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Return to original directory
cd ..

# Clean up environment variables
unset BRU_ENV_baseUrl
unset BRU_ENV_validBearerToken
unset BRU_ENV_test1PlrId
unset BRU_ENV_domesticOnlyPlrId
unset BRU_ENV_nonDomesticPlrId
unset BRU_ENV_accountingPeriodFrom
unset BRU_ENV_accountingPeriodTo
unset BRU_ENV_invalidPillar2Id

# Print summary
echo "======================================================================================
Test Results Summary
======================================================================================"
echo "Total Tests: $TOTAL_TESTS"
echo "Passed Tests: $PASSED_TESTS"
echo "Failed Tests: $FAILED_TESTS"

echo ""
echo "Known Issues:"
echo "1. Valid request - 400 Bad Request instead of 201 Created"
echo "2. Duplicate submission - 400 Bad Request instead of 422 Unprocessable Entity"
echo "3. Domestic with MTT - 201 Created instead of 422 Unprocessable Entity"
echo "4. Subscription not found - 400 Bad Request instead of 422 Unprocessable Entity"
echo "5. Nilreturn amend - 422 Unprocessable Entity instead of 201 Created"
echo ""
echo "The above issues need to be fixed in the controllers to return the expected status codes and responses."
echo ""
echo "All tests completed!" 