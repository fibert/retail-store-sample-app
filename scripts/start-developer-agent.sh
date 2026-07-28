#!/usr/bin/env bash
#
# start-run.sh — Operator helper to start one SDE Agent Run (design T3.4, §2, §3.1, §4.2).
#
# Thin, documented wrapper around `aws codebuild start-build` for the NO_SOURCE SDE Agent
# project. The task travels in `environmentVariablesOverride` (TASK / TARGET_REPO / MODEL);
# per-Run knobs (timeout, compute type, debug session) ride the same call.
#
# One build == one Run. The runId (the Run Registry key) is the FULL buildId returned by
# StartBuild: `<project>:<uuid>` (design §3.2, buildspec/README.md "runId scheme").
#
# Resource names default to the values baked into lib/config.ts:
#   project = sde-agent-run    registry table = sde-agent-run-registry
#   region  = us-east-1        token secret   = sde-agent/github-token
#
# See docs/operator-runbook.md for the end-to-end guide and docs/verification.md for the
# happy-path / failure-path checklists.

set -euo pipefail

# ------------------------------------------------------------------------------------------
# Defaults (mirror lib/config.ts NAMES + REGION). Overridable via flags / env.
# ------------------------------------------------------------------------------------------
PROJECT_NAME="${SDE_AGENT_PROJECT:-sde-agent-run}"
REGISTRY_TABLE="${SDE_AGENT_REGISTRY_TABLE:-sde-agent-run-registry}"
REGION="${AWS_REGION:-${SDE_AGENT_REGION:-us-east-1}}"

TASK=""
TASK_FILE=""
TASK_S3_URI=""
TARGET_REPO=""
SOURCE_BRANCH=""
MODEL=""
TIMEOUT_MINUTES=""
COMPUTE_TYPE=""
DEBUG_SESSION=0
DRY_RUN=0

PROG="$(basename "$0")"

# ------------------------------------------------------------------------------------------
usage() {
  cat <<EOF
$PROG — start one SDE Agent Run via CodeBuild StartBuild.

USAGE:
  $PROG --repo <github-repo> (--task <text> | --task-file <path> | --task-s3-uri <s3://...>) [options]

REQUIRED:
  --repo, -r <repo>          TARGET_REPO — the GitHub repo the agent clones and acts on
                             (e.g. https://github.com/owner/repo or owner/repo).
  Exactly one task source:
  --task, -t <text>          Inline task instructions (TASK env override).
  --task-file, -f <path>     Read the task text from a file (for multi-line / long tasks).
  --task-s3-uri <s3://...>   Pass an S3 URI as TASK for very large tasks (design §3.1 fallback).
                             NOTE: the buildspec must fetch the body; see the caveat below.

OPTIONS:
  --branch, -b <branch>      SOURCE_BRANCH — the branch to clone AND the PR base. The agent branches
                             off this branch and the PR is opened back against it. Supports names
                             with slashes (e.g. feature/foo). Omit → the repo default branch.
  --model, -m <id>           MODEL override. Must be on the allowlist (config.ts ALLOWED_MODEL_IDS):
                               global.anthropic.claude-opus-4-8            (default main model)
                               global.anthropic.claude-opus-4-8[1m]        (1M-context variant)
                               global.anthropic.claude-haiku-4-5-20251001  (background model)
                             Omit → system default (global.anthropic.claude-opus-4-8). An off-list
                             value is DENIED at the trigger by the OperatorTriggerPolicy condition key.
  --timeout <minutes>        timeoutInMinutesOverride — the wall-clock circuit breaker for this Run.
                             Clamped by CodeBuild to 5..2160 (36 h). Default = project default (60).
  --compute-type <type>      computeTypeOverride for large repos, e.g. BUILD_GENERAL1_LARGE,
                             BUILD_GENERAL1_2XLARGE (design OQ-5). Default = project compute (LARGE).
  --debug, -d                Set --debug-session-enabled so a human can attach via SSM Session
                             Manager (design §3.7, T7). Breakpoints are IGNORED without this flag.
  --project, -p <name>       CodeBuild project name (default: $PROJECT_NAME).
  --region <region>          AWS region (default: $REGION).
  --dry-run                  Print the aws command that would run; do not call StartBuild.
  --help, -h                 Show this help.

EXAMPLES:
  # Happy path (system default model):
  $PROG --repo owner/repo --task "Add a healthcheck endpoint and a test for it."

  # Fix a bug on a specific source branch (PR opens back against that branch):
  $PROG --repo fibert/retail-store-sample-app --branch bug --task "Fix the following bug..."

  # Long task from a file, bigger machine, tighter wall-clock breaker:
  $PROG -r owner/repo -f ./task.md --compute-type BUILD_GENERAL1_2XLARGE --timeout 120

  # Model override:
  $PROG -r owner/repo -t "Refactor the auth module." -m 'global.anthropic.claude-opus-4-8[1m]'

  # Interactive debug session (attach later via SSM — see docs/operator-runbook.md):
  $PROG -r owner/repo -t "Investigate the flaky integration test." --debug

LARGE-TASK S3-URI FALLBACK (design §3.1):
  StartBuild env-var payloads are size-bounded. For a task too large to fit in --task, upload the
  body to S3 and pass its URI with --task-s3-uri; the agent is expected to fetch it in pre_build.
  CAVEAT: verify the deployed buildspec actually fetches an s3:// TASK before relying on this — the
  current harness passes TASK verbatim as the prompt (see docs/operator-runbook.md "Known caveats").

AFTER IT STARTS:
  The script prints the buildId and the derived runId (they are the same string) plus ready-to-run
  commands to query the Run Registry and tail logs. See docs/operator-runbook.md.
EOF
}

die() { echo "$PROG: error: $*" >&2; exit 2; }

# ------------------------------------------------------------------------------------------
# Parse args.
# ------------------------------------------------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    --repo|-r)          TARGET_REPO="${2:-}"; shift 2 ;;
    --branch|-b)        SOURCE_BRANCH="${2:-}"; shift 2 ;;
    --task|-t)          TASK="${2:-}"; shift 2 ;;
    --task-file|-f)     TASK_FILE="${2:-}"; shift 2 ;;
    --task-s3-uri)      TASK_S3_URI="${2:-}"; shift 2 ;;
    --model|-m)         MODEL="${2:-}"; shift 2 ;;
    --timeout)          TIMEOUT_MINUTES="${2:-}"; shift 2 ;;
    --compute-type)     COMPUTE_TYPE="${2:-}"; shift 2 ;;
    --debug|-d)         DEBUG_SESSION=1; shift ;;
    --project|-p)       PROJECT_NAME="${2:-}"; shift 2 ;;
    --region)           REGION="${2:-}"; shift 2 ;;
    --dry-run)          DRY_RUN=1; shift ;;
    --help|-h)          usage; exit 0 ;;
    --)                 shift; break ;;
    -*)                 die "unknown flag: $1 (see --help)" ;;
    *)                  die "unexpected positional argument: $1 (see --help)" ;;
  esac
done

# ------------------------------------------------------------------------------------------
# Validate args.
# ------------------------------------------------------------------------------------------
command -v aws >/dev/null 2>&1 || die "the AWS CLI ('aws') is not on PATH."

[ -n "$TARGET_REPO" ] || die "--repo is required (see --help)."

# Exactly one task source.
task_sources=0
[ -n "$TASK" ] && task_sources=$((task_sources + 1))
[ -n "$TASK_FILE" ] && task_sources=$((task_sources + 1))
[ -n "$TASK_S3_URI" ] && task_sources=$((task_sources + 1))
[ "$task_sources" -eq 1 ] || die "provide exactly one of --task / --task-file / --task-s3-uri (got $task_sources)."

if [ -n "$TASK_FILE" ]; then
  [ -f "$TASK_FILE" ] || die "--task-file not found: $TASK_FILE"
  TASK="$(cat "$TASK_FILE")"
fi
if [ -n "$TASK_S3_URI" ]; then
  case "$TASK_S3_URI" in
    s3://*) TASK="$TASK_S3_URI" ;;
    *) die "--task-s3-uri must be an s3:// URI (got: $TASK_S3_URI)" ;;
  esac
fi

[ -n "$TASK" ] || die "the task is empty — provide non-empty --task/--task-file, or a valid --task-s3-uri."

if [ -n "$TIMEOUT_MINUTES" ]; then
  case "$TIMEOUT_MINUTES" in
    ''|*[!0-9]*) die "--timeout must be an integer number of minutes (got: $TIMEOUT_MINUTES)." ;;
  esac
  if [ "$TIMEOUT_MINUTES" -lt 5 ] || [ "$TIMEOUT_MINUTES" -gt 2160 ]; then
    die "--timeout must be 5..2160 minutes (CodeBuild ceiling is 36 h)."
  fi
fi

# ------------------------------------------------------------------------------------------
# Build the environment-variables-override payload as JSON (not the CLI's shorthand
# `name=..,value=..,type=..` form). The shorthand parser splits fields on commas and key/values
# on '=', so a TASK containing a comma, an '=', or parentheses breaks it ("Expected: '=' …").
# JSON has no such delimiter collision, so the task text can be arbitrary. jq builds it so the
# escaping (quotes, backslashes, newlines, unicode) is always correct.
# ------------------------------------------------------------------------------------------
command -v jq >/dev/null 2>&1 || die "jq is required to build the StartBuild payload; please install it (e.g. brew install jq)."
env_overrides_json="$(
  jq -nc \
    --arg task "$TASK" \
    --arg repo "$TARGET_REPO" \
    --arg branch "$SOURCE_BRANCH" \
    --arg model "$MODEL" \
    '[ {name:"TASK",        value:$task, type:"PLAINTEXT"},
       {name:"TARGET_REPO", value:$repo, type:"PLAINTEXT"} ]
     + (if $branch != "" then [ {name:"SOURCE_BRANCH", value:$branch, type:"PLAINTEXT"} ] else [] end)
     + (if $model != "" then [ {name:"MODEL", value:$model, type:"PLAINTEXT"} ] else [] end)'
)"

# Assemble the aws argv as an array (no eval, no word-splitting surprises).
aws_args=(
  codebuild start-build
  --project-name "$PROJECT_NAME"
  --region "$REGION"
  --environment-variables-override "$env_overrides_json"
)
[ -n "$TIMEOUT_MINUTES" ] && aws_args+=( --timeout-in-minutes-override "$TIMEOUT_MINUTES" )
[ -n "$COMPUTE_TYPE" ] && aws_args+=( --compute-type-override "$COMPUTE_TYPE" )
[ "$DEBUG_SESSION" -eq 1 ] && aws_args+=( --debug-session-enabled )

# ------------------------------------------------------------------------------------------
# Dry run: print the command (quoted) and exit.
# ------------------------------------------------------------------------------------------
if [ "$DRY_RUN" -eq 1 ]; then
  echo "[dry-run] would run:"
  printf 'aws'
  for a in "${aws_args[@]}"; do printf ' %q' "$a"; done
  printf '\n'
  exit 0
fi

# ------------------------------------------------------------------------------------------
# Fire StartBuild. Extract the buildId from the JSON response with the AWS CLI's own --query so
# we do not depend on jq.
# ------------------------------------------------------------------------------------------
echo "Starting Run on project '$PROJECT_NAME' (region $REGION)…" >&2
BUILD_ID="$(aws "${aws_args[@]}" --query 'build.id' --output text)"

if [ -z "$BUILD_ID" ] || [ "$BUILD_ID" = "None" ]; then
  die "StartBuild did not return a build id (check the command output above / your IAM permissions)."
fi

# runId == the full buildId, verbatim (design §3.2 two-writer agreement).
RUN_ID="$BUILD_ID"

cat <<EOF

Run started.
  buildId : $BUILD_ID
  runId   : $RUN_ID        (= the Run Registry partition key; same string as buildId)

Query the Run Registry (state PENDING→RUNNING→SUCCEEDED|FAILED|REJECTED|TIMED_OUT):
  aws dynamodb get-item \\
    --region $REGION \\
    --table-name $REGISTRY_TABLE \\
    --key '{"runId":{"S":"$RUN_ID"}}'

Tail the live CloudWatch logs for this build:
  aws codebuild batch-get-builds --region $REGION --ids "$BUILD_ID" \\
    --query 'builds[0].logs.{group:groupName,stream:streamName,deepLink:deepLink}'
  # then: aws logs tail <group> --log-stream-names <stream> --follow --region $REGION

Durable logs + transcript + failure report land in S3 under:
  s3://sde-agent-logs-<ACCOUNT_ID>-$REGION/runs/$RUN_ID/    (bucket name is deterministic)
EOF

if [ "$DEBUG_SESSION" -eq 1 ]; then
  cat <<EOF

Debug session enabled. Attach a shell to the running build via SSM Session Manager — see
docs/operator-runbook.md ("Attach an interactive debug session"). Autonomy is unaffected if
nobody attaches.
EOF
fi
