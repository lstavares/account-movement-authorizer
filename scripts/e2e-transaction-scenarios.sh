#!/usr/bin/env bash

set -Eeuo pipefail

START_STACK=false
CLEAN=false
BASE_URL="http://localhost:8080"
TEMP_DIR=""
HTTP_BODY_FILE=""
HTTP_BODY=""
HTTP_STATUS=""
ACCOUNT_ID=""
INITIAL_BALANCE=""
FINAL_BALANCE=""
RESULTS=()
METRICS=()

usage() {
  cat <<'USAGE'
Usage: bash scripts/e2e-transaction-scenarios.sh [--start-stack] [--clean] [--base-url http://localhost:8080]

Options:
  --start-stack          Build and start postgres, localstack and app before running scenarios.
  --clean                Only valid with --start-stack. Runs docker compose down -v --remove-orphans first.
  --base-url <url>       Application base URL. Defaults to http://localhost:8080.
  -h, --help             Show this help message.
USAGE
}

die() {
  echo "[ERROR] $*" >&2
  exit 1
}

log() {
  echo "[INFO] $*"
}

cleanup() {
  if [[ -n "${TEMP_DIR:-}" && -d "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
}

on_error() {
  local line="$1"
  local command="$2"
  echo "[ERROR] E2E smoke validation failed at line $line: $command" >&2
}

trap 'on_error "$LINENO" "$BASH_COMMAND"' ERR
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --start-stack)
      START_STACK=true
      shift
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --base-url)
      [[ $# -ge 2 ]] || die "--base-url requires a value"
      BASE_URL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      die "Unknown argument: $1"
      ;;
  esac
done

if [[ "$CLEAN" == "true" && "$START_STACK" != "true" ]]; then
  die "--clean is only accepted together with --start-stack"
fi

BASE_URL="${BASE_URL%/}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/account-authorizer-e2e.XXXXXX")"
HTTP_BODY_FILE="$TEMP_DIR/http-body.json"

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required"
}

compose() {
  docker compose "$@"
}

psql_scalar() {
  local query="$1"

  compose exec -T postgres psql \
    -U account_authorizer \
    -d account_authorizer \
    -Atc "$query" | tr -d '\r'
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "$actual" != "$expected" ]]; then
    die "$message. Expected '$expected', got '$actual'"
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local message="$3"

  if [[ "$haystack" != *"$needle"* ]]; then
    die "$message. Expected response to contain '$needle'. Response: $haystack"
  fi
}

record_result() {
  RESULTS+=("$1")
  echo "  OK - $1"
}

record_metric() {
  METRICS+=("$1")
  echo "  OK - metric series present: $1"
}

new_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
    return
  fi

  printf '00000000-0000-4000-8000-%04x%04x%04x\n' "$RANDOM" "$RANDOM" "$RANDOM"
}

cents_to_decimal() {
  local cents="$1"
  printf '%d.%02d' "$((cents / 100))" "$((cents % 100))"
}

transaction_payload() {
  local account_id="$1"
  local type="$2"
  local amount="$3"
  local currency="$4"

  printf '{"account":{"id":"%s"},"transaction":{"type":"%s","amount":{"value":%s,"currency":"%s"}}}' \
    "$account_id" "$type" "$amount" "$currency"
}

post_transaction() {
  local transaction_id="$1"
  local payload="$2"

  HTTP_STATUS="$(
    curl -sS -o "$HTTP_BODY_FILE" -w '%{http_code}' \
      -X POST "$BASE_URL/transactions/$transaction_id" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  )"
  HTTP_BODY="$(tr -d '\n\r' < "$HTTP_BODY_FILE")"
}

account_balance() {
  local account_id="$1"
  psql_scalar "select balance_amount from accounts where id = '$account_id';"
}

transaction_count() {
  local transaction_id="$1"
  psql_scalar "select count(*) from transactions where id = '$transaction_id';"
}

transaction_value() {
  local transaction_id="$1"
  local expression="$2"

  psql_scalar "select $expression from transactions where id = '$transaction_id';"
}

assert_transaction_count() {
  local transaction_id="$1"
  local expected="$2"

  assert_equals "$expected" "$(transaction_count "$transaction_id")" "Unexpected transaction count for $transaction_id"
}

assert_transaction_values() {
  local transaction_id="$1"
  local expected_status="$2"
  local expected_failure_reason="$3"
  local expected_amount_value="$4"
  local expected_amount_currency="$5"
  local expected_balance_before="$6"
  local expected_balance_after="$7"

  assert_transaction_count "$transaction_id" "1"
  assert_equals "$expected_status" "$(transaction_value "$transaction_id" "status")" "Unexpected status for $transaction_id"
  assert_equals "$expected_failure_reason" "$(transaction_value "$transaction_id" "coalesce(failure_reason, '')")" "Unexpected failure_reason for $transaction_id"
  assert_equals "$expected_amount_value" "$(transaction_value "$transaction_id" "amount_value")" "Unexpected amount_value for $transaction_id"
  assert_equals "$expected_amount_currency" "$(transaction_value "$transaction_id" "amount_currency")" "Unexpected amount_currency for $transaction_id"
  assert_equals "$expected_balance_before" "$(transaction_value "$transaction_id" "coalesce(balance_before_amount::text, 'NULL')")" "Unexpected balance_before_amount for $transaction_id"
  assert_equals "$expected_balance_after" "$(transaction_value "$transaction_id" "coalesce(balance_after_amount::text, 'NULL')")" "Unexpected balance_after_amount for $transaction_id"
}

nonexistent_account_id() {
  local candidate
  local count

  for _ in 1 2 3 4 5; do
    candidate="$(new_uuid)"
    count="$(psql_scalar "select count(*) from accounts where id = '$candidate';")"
    if [[ "$count" == "0" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  die "Could not generate a non-existing account id"
}

wait_for_app_health() {
  log "Waiting for application health at $BASE_URL/actuator/health"

  for _ in $(seq 1 60); do
    if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
      log "Application is healthy"
      return
    fi
    sleep 2
  done

  die "Application did not become healthy"
}

wait_for_accounts() {
  local count

  log "Waiting for at least one persisted account"
  for _ in $(seq 1 60); do
    if count="$(psql_scalar "select count(*) from accounts;" 2>/dev/null)" && [[ "$count" =~ ^[0-9]+$ ]] && (( count > 0 )); then
      log "Found $count persisted account(s)"
      return
    fi
    sleep 2
  done

  die "No accounts were persisted before timeout"
}

validate_postgres() {
  local result

  log "Validating PostgreSQL through docker compose"
  result="$(psql_scalar "select 1;")"
  assert_equals "1" "$result" "PostgreSQL validation failed"
}

validate_prometheus_endpoint() {
  log "Validating Prometheus endpoint at $BASE_URL/actuator/prometheus"
  curl -fsS "$BASE_URL/actuator/prometheus" >/dev/null
}

start_stack() {
  if [[ "$CLEAN" == "true" ]]; then
    log "Cleaning Docker Compose environment. This removes local volumes."
    compose down -v --remove-orphans
  fi

  log "Building app image"
  compose build app

  log "Starting postgres, localstack and app"
  compose up -d postgres localstack app

  wait_for_app_health

  log "Seeding SQS with a reduced E2E account batch"
  compose --profile seed run --rm -e TOTAL_ACCOUNTS=25 message-generator

  wait_for_accounts
}

select_account() {
  ACCOUNT_ID="$(
    psql_scalar "select id from accounts where status = 'ENABLED' order by created_at desc limit 1;"
  )"

  [[ -n "$ACCOUNT_ID" ]] || die "No ENABLED account was found"
  INITIAL_BALANCE="$(account_balance "$ACCOUNT_ID")"

  [[ "$INITIAL_BALANCE" =~ ^-?[0-9]+$ ]] || die "Invalid initial balance for account $ACCOUNT_ID: $INITIAL_BALANCE"

  log "Using accountId=$ACCOUNT_ID initialBalance=$INITIAL_BALANCE cents"
}

run_scenarios() {
  local tx_credit
  local tx_debit
  local tx_insufficient
  local tx_missing_account
  local tx_invalid_currency
  local tx_invalid_amount
  local missing_account_id
  local payload
  local expected_balance
  local current_balance
  local high_amount_cents
  local high_amount_decimal

  echo
  echo "Running E2E transaction scenarios"

  tx_credit="$(new_uuid)"
  payload="$(transaction_payload "$ACCOUNT_ID" "CREDIT" "100.00" "BRL")"
  post_transaction "$tx_credit" "$payload"
  assert_equals "200" "$HTTP_STATUS" "CREDIT should return HTTP 200"
  assert_contains "$HTTP_BODY" '"status":"SUCCEEDED"' "CREDIT response should contain SUCCEEDED"
  expected_balance="$((INITIAL_BALANCE + 10000))"
  assert_transaction_values "$tx_credit" "SUCCEEDED" "" "10000" "BRL" "$INITIAL_BALANCE" "$expected_balance"
  assert_equals "$expected_balance" "$(account_balance "$ACCOUNT_ID")" "CREDIT should increase account balance"
  record_result "CREDIT 100.00 BRL succeeded and increased balance by 10000 cents"

  tx_debit="$(new_uuid)"
  payload="$(transaction_payload "$ACCOUNT_ID" "DEBIT" "30.00" "BRL")"
  post_transaction "$tx_debit" "$payload"
  assert_equals "200" "$HTTP_STATUS" "DEBIT should return HTTP 200"
  assert_contains "$HTTP_BODY" '"status":"SUCCEEDED"' "DEBIT response should contain SUCCEEDED"
  current_balance="$expected_balance"
  expected_balance="$((current_balance - 3000))"
  assert_transaction_values "$tx_debit" "SUCCEEDED" "" "3000" "BRL" "$current_balance" "$expected_balance"
  assert_equals "$expected_balance" "$(account_balance "$ACCOUNT_ID")" "DEBIT should decrease account balance"
  record_result "DEBIT 30.00 BRL succeeded and reduced balance by 3000 cents"

  tx_insufficient="$(new_uuid)"
  current_balance="$expected_balance"
  high_amount_cents="$((current_balance + 100000))"
  high_amount_decimal="$(cents_to_decimal "$high_amount_cents")"
  payload="$(transaction_payload "$ACCOUNT_ID" "DEBIT" "$high_amount_decimal" "BRL")"
  post_transaction "$tx_insufficient" "$payload"
  assert_equals "200" "$HTTP_STATUS" "Insufficient funds DEBIT should return HTTP 200"
  assert_contains "$HTTP_BODY" '"status":"FAILED"' "Insufficient funds response should contain FAILED"
  assert_transaction_values "$tx_insufficient" "FAILED" "INSUFFICIENT_FUNDS" "$high_amount_cents" "BRL" "$current_balance" "$current_balance"
  assert_equals "$current_balance" "$(account_balance "$ACCOUNT_ID")" "Insufficient funds should not change account balance"
  record_result "DEBIT above current balance failed with INSUFFICIENT_FUNDS"

  tx_missing_account="$(new_uuid)"
  missing_account_id="$(nonexistent_account_id)"
  payload="$(transaction_payload "$missing_account_id" "CREDIT" "50.00" "BRL")"
  post_transaction "$tx_missing_account" "$payload"
  assert_equals "200" "$HTTP_STATUS" "Missing account CREDIT should return HTTP 200"
  assert_contains "$HTTP_BODY" '"status":"FAILED"' "Missing account response should contain FAILED"
  assert_transaction_values "$tx_missing_account" "FAILED" "ACCOUNT_NOT_FOUND" "5000" "BRL" "NULL" "NULL"
  record_result "CREDIT for a missing account failed with ACCOUNT_NOT_FOUND"

  payload="$(transaction_payload "$ACCOUNT_ID" "CREDIT" "100.00" "BRL")"
  post_transaction "$tx_credit" "$payload"
  assert_equals "200" "$HTTP_STATUS" "Idempotent replay should return HTTP 200"
  assert_contains "$HTTP_BODY" '"status":"SUCCEEDED"' "Idempotent replay response should contain SUCCEEDED"
  assert_transaction_count "$tx_credit" "1"
  assert_equals "$current_balance" "$(account_balance "$ACCOUNT_ID")" "Idempotent replay should not change account balance"
  record_result "Idempotent replay with the same payload returned the original result"

  payload="$(transaction_payload "$ACCOUNT_ID" "CREDIT" "101.00" "BRL")"
  post_transaction "$tx_credit" "$payload"
  assert_equals "409" "$HTTP_STATUS" "Idempotency conflict should return HTTP 409"
  assert_transaction_count "$tx_credit" "1"
  assert_equals "$current_balance" "$(account_balance "$ACCOUNT_ID")" "Idempotency conflict should not change account balance"
  record_result "Idempotency conflict returned HTTP 409 without creating another transaction"

  tx_invalid_currency="$(new_uuid)"
  payload="$(transaction_payload "$ACCOUNT_ID" "CREDIT" "10.00" "USD")"
  post_transaction "$tx_invalid_currency" "$payload"
  assert_equals "400" "$HTTP_STATUS" "Invalid currency should return HTTP 400"
  assert_transaction_count "$tx_invalid_currency" "0"
  record_result "Invalid currency USD returned HTTP 400 without persisting a transaction"

  tx_invalid_amount="$(new_uuid)"
  payload="$(transaction_payload "$ACCOUNT_ID" "CREDIT" "0" "BRL")"
  post_transaction "$tx_invalid_amount" "$payload"
  assert_equals "400" "$HTTP_STATUS" "Invalid amount should return HTTP 400"
  assert_transaction_count "$tx_invalid_amount" "0"
  record_result "Invalid amount 0 returned HTTP 400 without persisting a transaction"

  FINAL_BALANCE="$(account_balance "$ACCOUNT_ID")"
}

validate_metrics() {
  local prometheus_output
  local series

  echo
  echo "Validating Prometheus metric series"

  prometheus_output="$(curl -fsS "$BASE_URL/actuator/prometheus")"

  for series in \
    "transaction_authorizations_total" \
    "transaction_authorization_failures_total" \
    "transaction_authorization_duration_seconds_count" \
    "account_opening_messages_total" \
    "sqs_poll_messages_received_total"
  do
    assert_contains "$prometheus_output" "$series" "Prometheus output should contain $series"
    record_metric "$series"
  done
}

print_summary() {
  local item

  echo
  echo "E2E smoke validation passed"
  echo "Base URL: $BASE_URL"
  echo "Account ID: $ACCOUNT_ID"
  echo "Initial balance: $INITIAL_BALANCE cents"
  echo "Final balance: $FINAL_BALANCE cents"
  echo
  echo "Scenarios validated:"
  for item in "${RESULTS[@]}"; do
    echo "  - $item"
  done
  echo
  echo "Metric series validated:"
  for item in "${METRICS[@]}"; do
    echo "  - $item"
  done
}

main() {
  require_command curl
  require_command docker

  if [[ "$START_STACK" == "true" ]]; then
    start_stack
  else
    wait_for_app_health
  fi

  validate_postgres
  validate_prometheus_endpoint
  select_account
  run_scenarios
  validate_metrics
  print_summary
}

main
