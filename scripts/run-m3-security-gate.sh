#!/usr/bin/env bash
set -euo pipefail

./mvnw --batch-mode test \
  -Dtest=EventIngestionControllerTest,LocalTargetPolicyTest,HttpDeliveryDispatcherTest,WebhookSignatureTest,SecretProtectorTest,TenantRateLimitServiceIntegrationTest,DeliveryClaimServiceIntegrationTest

# These checks are intentionally independent of Java tests so configuration drift fails the gate.
! rg -n --glob '!src/test/**' 'spring\.datasource\.(username|password)=.*:-' src/main/resources
! rg -n --glob '!src/test/**' 'RELAY_SECRETS_ENCRYPTION_KEY.*:-' src/main/resources
! rg -n --glob '*.java' '(printStackTrace\(|System\.out\.print)' src/main
