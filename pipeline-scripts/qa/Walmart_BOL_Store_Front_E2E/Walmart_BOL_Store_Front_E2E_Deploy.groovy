import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _


//Git parametee

credsName = 'scm_deployment'
gitRepository="https://github.com/InComm-Software-Development/walmartbol_storefront.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

test = "test"
target = "sqlatsttaf01v.unx.incommtech.net"

emailDistribution="rkale@incomm.com ppiermarini@incomm.com vrevuri@incomm.com"

destLoc = "/tmp"
source = "src/*"

currentBuild.result="SUCCESS"
node('linux'){
	try { 
			
		cleanWs()
		//testing the checkout
		stage('Github Checkout'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
			//TODO: May not need this after 
		
		}

		stage('Deployment'){
			deploy(target)

		}
			
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		notifyBuild(emailDistribution)
		}
	}

} //end of node


def deploy(target_hostname) {
	//scp -o StrictHostKeyChecking=no -r src/* root@${target_hostname}:${configFolderLoc}
    //scp -o StrictHostKeyChecking=no version.txt root@${target_hostname}:${artifactDeploymentLoc}/
	echo " the target is: ${target_hostname}"
	//TODO: Modify the logic to scp over the required folders to run the mvn tests
	sshagent([credsName]) {
		sh """scp -i ~/.ssh/pipeline -r -q -o StrictHostKeyChecking=no ${source} root@${target_hostname}:${destLoc}
					ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'google-chrome --version && mvn -v && java -version'
					ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'cd ${destLoc} && mvn clean test -DSuite=${Suite} -Drunmode=${RunMode} -Denv=${Environment} --disable-gpu,--no-sandbox,--disable-dev-shm-usage'"""
}

}

	

def notifyBuild(recipients) {
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Deploy for Walmart_BOL_Store_Front_E2E", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End