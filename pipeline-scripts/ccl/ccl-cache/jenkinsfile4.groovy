import hudson.model.*
import jenkins.model.*
@Library('pipeline-shared-library') _

targets = [
	'dev-a': ['sdcclappa01v.unx.incommtech.net'],
    'dev-b': ['sdcclappb01v.unx.incommtech.net']
]

myData = [
	'gitUrl' : 'https://github.com/InComm-Software-Development/ccl-cache.git',
	'gitBranch' : "${BRANCH}",
	'groupId' : 'com.incomm.cclp',
	'artifactId' : 'ccl-cache',
	'artExtension' : 'jar',
	'artifactName' : '',
	'artifactType' : 'jar',
	'artifactDeploymentLoc': '/srv/ccl-apps/ccl-cache',
	'serviceName': 'ccl-cache.service',
	'run_junit_tests' : 'false',
	'sonarqube_scan' : 'false',
	'target_env' : 'dev-a',
	'test_suite' : 'none',
	'artifactVersion' : '0.0.1'
]
cclSpring_v1(targets, myData)
