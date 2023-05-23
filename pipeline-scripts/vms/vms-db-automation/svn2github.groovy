import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _
Branch="https://svn.incomm.com/svn/fs/GPR/Database/tags"
gitCreds = "scm-incomm"
credsName = "scm_deployment"
gitRepository = "https://github.com/InComm-Software-Development/vms-db-automation.git"
BRANCH = "master"


node('linux2'){

    stage('svnCheckout'){
		cleanWs()
		def svnTag = "${Branch}/${svnTagName}"
		checkoutSVN(svnTag)

	}
	
	stage('copyZip'){ //APR_VMSGPRHOST_R60.1_RELEASE.zip  sdlanststmas09cv
		
		sh " cp Releasepatch/${zipPackageName}_RELEASE.zip ."

	}
	
    stage('gitCheckout'){
		dir('github'){	
			githubCheckout(gitCreds, gitRepository, "${BRANCH}")
			sshagent([credsName]) {
			sh """
				git checkout --orphan ${gitBranchName}
				git rm -rf .
				cp ../${zipPackageName}_RELEASE.zip .
				git add .
				git commit -m\"${zipPackageName}_RELEASE\"
				git push --set-upstream origin ${gitBranchName}
			"""
			}

		}
	}

}