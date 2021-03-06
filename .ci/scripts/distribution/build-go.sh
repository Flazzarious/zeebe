#!/bin/bash -eux
set -o pipefail

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

go get -u github.com/jstemmer/go-junit-report

cd ${ORG_DIR}/zeebe/clients/go
make install-deps

cd ${ORG_DIR}/zeebe/clients/zbctl

./build.sh
