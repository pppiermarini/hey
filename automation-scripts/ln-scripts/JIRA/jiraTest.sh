#!/bin/bash

function test {
	echo "initiating the call"
	curl -u $1:$2 https://jira-tst.incomm.com/rest/api/2/project/SCMTEST/roles
}

test $1 $2
