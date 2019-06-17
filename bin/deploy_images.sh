#!/usr/bin/env bash

set -euo pipefail

docker pull centos:centos7

docker_login() {
    local restore_x
    if [[ $- =~ x ]]; then
      restore_x=true
    else
      restore_x=false
    fi

    set +x
    echo "${DOCKER_PASSWORD}" | docker login -u ${DOCKER_USERNAME} --password-stdin
    if [[ ${restore_x} ]]; then
        set -x
    fi
}

docker_login

echo "Building images for version: $VERSION"

# Workflow UI container
WORKFLOW_UI_REPO="liveramp/workflow2_ui:${VERSION}"
pushd workflow_ui
docker build -t workflow2_ui:${VERSION} -f Dockerfile .
docker tag workflow2_ui ${WORKFLOW_UI_REPO}
docker push ${WORKFLOW_UI_REPO}
popd

# Workflow monitor container
WORKFLOW_MONITOR_REPO="liveramp/workflow2_monitor:${VERSION}"
pushd workflow_monitor
docker build -t workflow2_monitor:${VERSION} -f Dockerfile .
docker tag workflow2_monitor ${WORKFLOW_MONITOR_REPO}
docker push ${WORKFLOW_MONITOR_REPO}
popd

# Workflow examples container
WORKFLOW_EXAMPLES_REPO="liveramp/workflow2_examples:${VERSION}"
pushd workflow_examples
docker build -t workflow2_examples:${VERSION} -f Dockerfile .
docker tag workflow2_examples ${WORKFLOW_EXAMPLES_REPO}
docker push ${WORKFLOW_EXAMPLES_REPO}
popd
