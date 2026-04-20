#!/bin/bash
set -e

create_table() {
  local name=$1
  shift
  if awslocal dynamodb describe-table --table-name "$name" > /dev/null 2>&1; then
    echo "Table '$name' already exists — skipping"
  else
    awslocal dynamodb create-table --table-name "$name" "$@"
    echo "Table '$name' created"
  fi
}

echo "==> Creating DynamoDB tables"

create_table account-posting \
  --attribute-definitions \
    AttributeName=postingId,AttributeType=N \
    AttributeName=endToEndReferenceId,AttributeType=S \
    AttributeName=status,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
    AttributeName=sourceName,AttributeType=S \
  --key-schema AttributeName=postingId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[
    {
      "IndexName": "gsi-endToEndReferenceId",
      "KeySchema": [{"AttributeName":"endToEndReferenceId","KeyType":"HASH"}],
      "Projection": {"ProjectionType":"ALL"}
    },
    {
      "IndexName": "gsi-status-createdAt",
      "KeySchema": [{"AttributeName":"status","KeyType":"HASH"},{"AttributeName":"createdAt","KeyType":"RANGE"}],
      "Projection": {"ProjectionType":"ALL"}
    },
    {
      "IndexName": "gsi-sourceName-createdAt",
      "KeySchema": [{"AttributeName":"sourceName","KeyType":"HASH"},{"AttributeName":"createdAt","KeyType":"RANGE"}],
      "Projection": {"ProjectionType":"ALL"}
    }
  ]'

create_table account-posting-leg \
  --attribute-definitions \
    AttributeName=postingId,AttributeType=N \
    AttributeName=transactionOrder,AttributeType=N \
  --key-schema \
    AttributeName=postingId,KeyType=HASH \
    AttributeName=transactionOrder,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

create_table account-posting-config \
  --attribute-definitions \
    AttributeName=requestType,AttributeType=S \
    AttributeName=orderSeq,AttributeType=N \
  --key-schema \
    AttributeName=requestType,KeyType=HASH \
    AttributeName=orderSeq,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

echo "==> Creating SQS queue"
if awslocal sqs get-queue-url --queue-name posting-queue > /dev/null 2>&1; then
  echo "Queue 'posting-queue' already exists — skipping"
else
  awslocal sqs create-queue --queue-name posting-queue
  echo "Queue 'posting-queue' created"
fi

echo "==> Creating SNS topic"
awslocal sns create-topic --name posting-alerts > /dev/null
echo "SNS topic 'posting-alerts' ready"

echo "==> LocalStack init complete"
