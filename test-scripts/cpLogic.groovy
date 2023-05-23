import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _

@Field projectProperties

//globals

//emailDistribution="rkale@incomm.com "

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

//gitRepository="${GIT_REPO}"
//gitBranch="${Branch}"
def gitCredentials = 'scm-incomm' 
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${BRANCH}"
allSnaps = []
imageLabel=''
emailDistribution = ''
imageName = ''
dockerRepo = 'docker.maven.incomm.com'
approvalData = [
	'operators': "[vhari,ppiermarini,rkale]",
	'adUserOrGroup' : 'vhari,ppiermarini',
	'target_env' : "${targetenvs}"

]
//projectProperties = ''
targetsMaster = []
targetsAll = []
node('linux1'){

	try { 
		cleanWs()

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }

        stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'test-yamls/dataDrivenDocker.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }
            echo "${projectProperties}"
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }

        }

        stage('Define Targets') {
        master = "${targetenvs}" + "-master"	
        current_targets=projectProperties.applications."${APPLICATION_TYPE}"."${master}".trim()

        all = "${targetenvs}" + "-all"
        current_targets_all=projectProperties.applications."${APPLICATION_TYPE}"."${all}"

        targets_str = targetsMaster.add(current_targets)
        targetsAll_str = current_targets_all.split(",")

        for (String targetA : targetsAll_str) {
                    filter_targetA = targetA.trim()
                    targetsAll.add(filter_targetA)
        	}

        uploadCerts(targetenvs, targetsAll)
        updateCert(targetenvs, targetsMaster)

        }


	} catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}
}

} //end of node

def uploadCerts(envName, targets){
    
    echo "my env= ${envName}"
    def stepsInParallel =  targets.collectEntries {
        [ "$it" : { deployCerts(it, envName) } ]
    }
    parallel stepsInParallel   
}


def deployCerts(target_hostname, envName) {
    echo "the target is: ${target_hostname}"
    echo "Env is: ${envName}"

}

def updateCert(envName, targets){
    
    echo "my env= ${envName}"
    deployUpdate(targets[0], envName)
}


def deployUpdate(target_hostname, envName) {
    echo "the target is: ${target_hostname}"
    echo "Env is: ${envName}"

}

///// The End