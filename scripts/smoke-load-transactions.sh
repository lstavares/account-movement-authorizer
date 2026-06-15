#!/usr/bin/env bash

set -u

ACCOUNT_ID="${1:-}"
REQUESTS="${2:-100}"
CONCURRENCY="${3:-8}"
BASE_URL="${4:-http://localhost:8080}"

usage() {
  echo "Usage: bash scripts/smoke-load-transactions.sh <accountId> [requests=100] [concurrency=8] [baseUrl=http://localhost:8080]"
}

if [[ -z "$ACCOUNT_ID" ]]; then
  usage
  exit 1
fi

if ! [[ "$REQUESTS" =~ ^[0-9]+$ ]] || [[ "$REQUESTS" -lt 1 ]]; then
  echo "requests must be a positive integer"
  exit 1
fi

if ! [[ "$CONCURRENCY" =~ ^[0-9]+$ ]] || [[ "$CONCURRENCY" -lt 1 ]]; then
  echo "concurrency must be a positive integer"
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required"
  exit 1
fi

STATUSES_FILE="$(mktemp -t account-authorizer-statuses.XXXXXX)"
trap 'rm -f "$STATUSES_FILE"' EXIT

new_transaction_id() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
    return
  fi

  printf '00000000-0000-4000-8000-%04x%04x%04x\n' "$RANDOM" "$RANDOM" "$RANDOM"
}

send_request() {
  local index="$1"
  local transaction_id
  local type
  local amount
  local payload
  local status

  transaction_id="$(new_transaction_id)"

  if (( index % 2 == 0 )); then
    type="CREDIT"
    amount="1.00"
  else
    type="DEBIT"
    amount="0.01"
  fi

  payload="$(printf '{"account":{"id":"%s"},"transaction":{"type":"%s","amount":{"value":%s,"currency":"BRL"}}}' "$ACCOUNT_ID" "$type" "$amount")"

  if ! status="$(
    curl -sS -o /dev/null -w '%{http_code}' \
      -X POST "$BASE_URL/transactions/$transaction_id" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  )"; then
    status="curl_error"
  fi

  printf '%s\n' "$status" >> "$STATUSES_FILE"
}

export ACCOUNT_ID BASE_URL STATUSES_FILE
export -f new_transaction_id send_request

echo "Running local smoke/load against $BASE_URL"
echo "accountId=$ACCOUNT_ID requests=$REQUESTS concurrency=$CONCURRENCY"
echo "This changes the local account balance and is not a scientific benchmark."

SECONDS=0
seq 1 "$REQUESTS" | xargs -n 1 -P "$CONCURRENCY" bash -c 'send_request "$1"' _
ELAPSED_SECONDS="$SECONDS"

echo
echo "Executed requests: $REQUESTS"
echo "Elapsed time: ${ELAPSED_SECONDS}s"
echo "HTTP status summary:"
sort "$STATUSES_FILE" | uniq -c | awk '{ printf "  HTTP %s: %s\n", $2, $1 }'

echo
echo "Inspect operational metrics with:"
echo "  curl -s $BASE_URL/actuator/prometheus | grep -E 'transaction_authorizations|transaction_authorization_failures|transaction_authorization_duration'"
echo "  curl -s $BASE_URL/actuator/prometheus | grep -E 'account_opening_messages|account_opening_processing_duration|sqs_poll_messages_received'"
