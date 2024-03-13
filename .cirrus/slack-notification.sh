#!/bin/bash

set -euo pipefail

curl -X POST https://slack.com/api/chat.postMessage \
  -H "Authorization: Bearer ${SLACK_TOKEN}" \
  -H 'Content-type: application/json; charset=utf-8' \
  --data-binary @- <<EOF
{
  "channel": "squad-ide-intellij-family-bots",
	"blocks": [
		{
			"type": "header",
			"text": {
				"type": "plain_text",
				"text": "Cirrus CI build failure on pipeline"
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Task *<https://cirrus-ci.com/task/$CIRRUS_TASK_ID|$CIRRUS_TASK_NAME>* failed on *<$CIRRUS_REPO_CLONE_URL|$CIRRUS_REPO_FULL_NAME>* (*$CIRRUS_BRANCH*)"
			}
		}
	]
}
EOF
