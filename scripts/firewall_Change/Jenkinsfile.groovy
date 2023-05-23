import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _
gitCreds = "scm-incomm"

credsName = "scm_deployment"
BRANCH_SCOPE = "master"
gitRepository = "https://github.com/InComm-Software-Development/hello-world.git"
svnRepository = "https://svn.incomm.com/svn/scm/pipeline_scripts"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

node('linux'){

	stage('checkout_Github'){
		cleanWs()
		githubCheckout(gitCreds, gitRepository, "${BRANCH_SCOPE}")
		sh"pwd && ls -ltr"
	}
	stage('checkout_svn'){
		//cleanWs()
		dir('svnCheckout'){
		checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[cancelProcessOnExternalsFail: true, credentialsId: '7a6d9c35-019c-485e-9b11-b92dcc3e4866', depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: 'https://svn.incomm.com/svn/scm/pipeline_scripts']], quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
		sh"pwd && ls -ltr"
		}
		
	}
	

	stage('compile-SonarQube-and-upload-to-Artifactory'){
		
		withSonarQubeEnv('sonarqube.incomm.com'){
			sh "${maven} clean deploy -X sonar:sonar" //-Dsonar.branch.name=${gitBranch}
		}
		
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
