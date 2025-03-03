#!/bin/bash

# Color codes for better visibility
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if MongoDB is running
check_mongodb() {
  echo "Checking if MongoDB is running..."
  if ! mongosh --eval "db.version()" >/dev/null 2>&1; then
    echo -e "${RED}MongoDB is not running. Please start MongoDB before running this script.${NC}"
    exit 1
  fi
  echo -e "${GREEN}MongoDB is running.${NC}"
}

# Function to create a test subscription
create_subscription() {
  local plr_id=$1
  local result=$(curl -s -X GET "http://localhost:10055/pillar2/subscription/$plr_id" -H "Content-Type: application/json")
  if echo "$result" | grep -q "subscription not found"; then
    echo "Creating subscription for $plr_id..."
    # This ensures that the GET subscription endpoint works correctly for our test IDs
    curl -s -X POST "http://localhost:10055/pillar2/test/organisation/$plr_id" \
      -H "Content-Type: application/json" \
      -d '{"orgDetails":{"domesticOnly":true,"organisationName":"SubscriptionTestCompany","registrationDate":"2024-01-01"},"accountingPeriod":{"startDate":"2024-01-01","endDate":"2024-03-31"}}' > /dev/null
  else
    echo "Subscription already exists for $plr_id"
  fi
}

# Function to create organization using curl
create_organization() {
  local plr_id=$1
  local is_domestic=$2
  local name=$3
  local start_date=$4
  local end_date=$5
  
  echo "Creating organisation with ID $plr_id using curl..."
  local response=$(curl -s -X POST "http://localhost:10055/pillar2/test/organisation/$plr_id" \
    -H "Content-Type: application/json" \
    -d "{\"orgDetails\":{\"domesticOnly\":$is_domestic,\"organisationName\":\"$name\",\"registrationDate\":\"2024-01-01\"},\"accountingPeriod\":{\"startDate\":\"$start_date\",\"endDate\":\"$end_date\"}}")
  
  if echo "$response" | grep -q "ORGANISATION_EXISTS"; then
    echo -e "${YELLOW}Organisation with ID $plr_id already exists${NC}"
  elif echo "$response" | grep -q "pillar2Id"; then
    echo -e "${GREEN}Successfully created organisation with ID $plr_id${NC}"
  else
    echo -e "${RED}Failed to create organisation with ID $plr_id: $response${NC}"
    return 1
  fi
  
  return 0
}

# Check if MongoDB is running
check_mongodb

# Delete the MongoDB database to start with a clean state
echo "Dropping MongoDB database..."
./delete_mongo_db.sh pillar2-external-test-stub

# Set environment variables for Bruno tests
export BRU_ENV_baseUrl="http://localhost:10055"
export BRU_ENV_validBearerToken="Bearer valid_token"
export BRU_ENV_testPlrId="XEPLR1234567890"
export BRU_ENV_test1PlrId="XEPLR1234567891"
export BRU_ENV_test2PlrId="XEPLR1234567892"
export BRU_ENV_domesticOnlyPlrId="XEPLR1234567891"
export BRU_ENV_nonDomesticPlrId="XEPLR1234567892"
export BRU_ENV_accountingPeriodFrom="2024-01-01"
export BRU_ENV_accountingPeriodTo="2024-03-31"
export BRU_ENV_accountingPeriod1From="2024-01-01"
export BRU_ENV_accountingPeriod1To="2024-03-31"
export BRU_ENV_accountingPeriod2From="2024-04-01"
export BRU_ENV_accountingPeriod2To="2024-06-30"
export BRU_ENV_accountingPeriod3From="2024-07-01"
export BRU_ENV_accountingPeriod3To="2024-09-30"
export BRU_ENV_invalidPillar2Id="XMPLR9999999999"

echo "======================================================================================"
echo -e "${GREEN}Running tests with Bruno CLI 1.0.0...${NC}"
echo "======================================================================================"

# Initialize results tracking
PASSED_TESTS=0
FAILED_TESTS=0
TOTAL_TESTS=0
FAILED_TEST_NAMES=()

# Step 1: Create organisations and subscriptions
echo "======================================================================================"
echo -e "${GREEN}Creating test organisations${NC}"
echo "======================================================================================"

# Create organizations using curl instead of Bruno
create_organization "XEPLR1234567891" "true" "DomesticTestCompany" "2024-01-01" "2024-03-31"
create_organization "XEPLR1234567892" "false" "NonDomesticTestCompany" "2024-01-01" "2024-03-31"
create_organization "XMPLR0012345674" "true" "TestCompany" "2024-08-14" "2024-12-14"

# For display purposes only, try running the Bruno scripts but don't rely on their success
echo -e "\n${YELLOW}Running Bruno organisation creation scripts (for logging purposes only)...${NC}"
# Create test directory to work from (Bruno requires running from collection root)
cd "API Testing" || { echo "API Testing directory not found"; exit 1; }

echo "Attempting domestic organisation creation with Bruno..."
npx @usebruno/cli run uktr/Create\ Organisation.bru --env local > /dev/null 2>&1
echo "Attempting non-domestic organisation creation with Bruno..."
npx @usebruno/cli run uktr/Create\ Non-Domestic\ Organisation.bru --env local > /dev/null 2>&1
echo "Attempting organisation with different accounting period with Bruno..."
npx @usebruno/cli run uktr/Create\ Organisation\ Different\ Period.bru --env local > /dev/null 2>&1

# Ensure subscriptions exist for test organisations
echo -e "${YELLOW}Setting up subscriptions for test organisations...${NC}"
create_subscription "XEPLR1234567891"
create_subscription "XEPLR1234567892"
create_subscription "XMPLR0012345674"

# Function to run a test and track its status
run_test() {
  local test_file=$1
  local test_name=$(basename "$test_file")
  
  echo "Running test: $test_name"
  npx @usebruno/cli run "$test_file" --env local
  
  if [ $? -eq 0 ]; then
    PASSED_TESTS=$((PASSED_TESTS+1))
    echo -e "  - ${GREEN}PASSED${NC}: $test_name"
  else
    FAILED_TESTS=$((FAILED_TESTS+1))
    FAILED_TEST_NAMES+=("$test_name")
    echo -e "  - ${RED}FAILED${NC}: $test_name"
  fi
  TOTAL_TESTS=$((TOTAL_TESTS+1))
  echo ""
}

# Step 2: Run submission tests
echo "======================================================================================"
echo -e "${GREEN}Running submission tests in specified order${NC}"
echo "======================================================================================"

# Run tests in the specified order
echo "Running Step 3: No Pillar2Id test"
run_test "uktr/submitUKTR/No Pillar2Id.bru"

echo "Running Step 4: Subscription not found test"
run_test "uktr/submitUKTR/subscription not found.bru"

echo "Running Step 5: Valid request test"
run_test "uktr/submitUKTR/valid request.bru"

echo "Running Step 6: NIL_RETURN valid request test"
run_test "uktr/submitUKTR/nilreturn - valid request.bru"

echo "Running Step 7: Duplicate submission test"
run_test "uktr/submitUKTR/duplicate submission.bru"

echo "Running Step 8: Invalid accounting period test"
run_test "uktr/submitUKTR/invalid accounting period.bru"

echo "Running Step 9: Domestic with MTT test"
run_test "uktr/submitUKTR/domestic with MTT.bru"

echo "Running Step 10: Empty liable entities test"
run_test "uktr/submitUKTR/empty liable entities.bru"

# Step 3: Run amendment tests
echo "======================================================================================"
echo -e "${GREEN}Running amendment tests${NC}"
echo "======================================================================================"

echo "Running Step 11: Amend submission test"
run_test "uktr/submitUKTR/amend submission.bru"

echo "Running Step 12: NIL return amend test"
run_test "uktr/submitUKTR/nilreturn - amend.bru"

# Return to original directory
cd ..

# Clean up environment variables
unset BRU_ENV_baseUrl
unset BRU_ENV_validBearerToken
unset BRU_ENV_testPlrId
unset BRU_ENV_test1PlrId
unset BRU_ENV_test2PlrId
unset BRU_ENV_domesticOnlyPlrId
unset BRU_ENV_nonDomesticPlrId
unset BRU_ENV_accountingPeriodFrom
unset BRU_ENV_accountingPeriodTo
unset BRU_ENV_accountingPeriod1From
unset BRU_ENV_accountingPeriod1To
unset BRU_ENV_accountingPeriod2From
unset BRU_ENV_accountingPeriod2To
unset BRU_ENV_accountingPeriod3From
unset BRU_ENV_accountingPeriod3To
unset BRU_ENV_invalidPillar2Id

# Print summary
echo "======================================================================================"
echo -e "${GREEN}Test Results Summary${NC}"
echo "======================================================================================"
echo "Total Tests: $TOTAL_TESTS"
if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
  echo -e "${GREEN}Passed Tests: $PASSED_TESTS${NC}"
  echo -e "${GREEN}Failed Tests: $FAILED_TESTS${NC}"
  echo -e "\n${GREEN}All tests passed successfully!${NC}"
else
  echo -e "Passed Tests: $PASSED_TESTS"
  echo -e "${RED}Failed Tests: $FAILED_TESTS${NC}"
  
  echo -e "\n${RED}Failed Test Names:${NC}"
  for test_name in "${FAILED_TEST_NAMES[@]}"; do
    echo -e "${RED}- $test_name${NC}"
  done
  
  echo -e "\n${YELLOW}Troubleshooting Tips:${NC}"
  echo "1. Check the logs/pillar2-external-test-stub.log file for detailed error information."
  echo "2. Ensure JSON structures in test requests match controller expectations."
  echo "3. Check if environment variables are correctly set and used in test files."
  echo "4. Verify accounting periods match what's set in the test organisations."
  echo "5. Make sure your application is running on port 10055."
  echo "6. Ensure your application is started with: sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'"
fi

echo ""
echo -e "${GREEN}All tests completed!${NC}" 