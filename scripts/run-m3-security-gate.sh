#!/usr/bin/env bash
set -euo pipefail

./mvnw --batch-mode test -Dtest=EventIngestionControllerTest,LocalTargetPolicyTest,HttpDeliveryDispatcherTest,WebhookSignatureTest,SecretProtectorTest,TenantRateLimitServiceIntegrationTest,DeliveryClaimServiceIntegrationTest

# These checks are intentionally independent of Java tests so configuration drift fails the gate.
if rg -n --glob '!src/test/**' 'spring\.datasource\.(username|password)\s*=\s*\$\{[^}:]+:[^}]*\}' src/main/resources; then
  echo 'Production datasource credentials must not define Spring placeholder defaults.' >&2
  exit 1
fi
if rg -n --glob '!src/test/**' 'relay\.secrets\.encryption-key\s*=\s*\$\{[^}:]+:[^}]*\}' src/main/resources; then
  echo 'The encryption key must not define a Spring placeholder default.' >&2
  exit 1
fi
if rg -n --glob '*.java' '(printStackTrace\(|System\.out\.print)' src/main; then
  echo 'Application code must not print potentially sensitive data directly.' >&2
  exit 1
fi
