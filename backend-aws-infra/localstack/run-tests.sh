#!/bin/bash
set -e

SCRIPT_DIR="$(dirname "$0")"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCOUNT_REGION=ap-southeast-1
export POSTING_TABLE_NAME=account-posting
export LEG_TABLE_NAME=account-posting-leg
export CONFIG_TABLE_NAME=account-posting-config
export PROCESSING_QUEUE_URL=http://localhost:4566/000000000000/posting-queue
export SUPPORT_ALERT_TOPIC_ARN=arn:aws:sns:ap-southeast-1:000000000000:posting-alerts

echo "==> Running backend-aws integration tests"
cd "$SCRIPT_DIR/../../backend-aws"
mvn test -Plocalstack

echo "==> Running backend-ops-aws integration tests"
cd "$SCRIPT_DIR/../../backend-ops-aws"
mvn test -Plocalstack

echo "==> All integration tests passed"
