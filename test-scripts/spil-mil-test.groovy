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

targetsMasterSpil = [
    'dev':  ['v1'],
    'qa': ['v2']

]


targetsAllSpil = [
    'dev':  ['v1','v12','v13'],
    'qa': ['v2','v22','v23']
]

targetsMasterMil = [
    'dev':  ['v3'],
    'qa': ['v4'],

]


targetsAllMil = [
    'dev':  ['v3','v32','v33'],
    'qa': ['v4','v42','v43']
]



targetsMasterSecureSpil = [
    'dev':  ['v5'],
    'qa': ['v6'],

]


targetsAllSecureSpil = [
    'dev':  ['v5','v52','v53'],
    'qa': ['v6','v62','v63']
]
node('linux1'){

	try { 
		cleanWs()

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }

        stage('Define Targets') {
            defineTargets()
            echo "${targetsMaster}"
            echo "${targetsAll}"
            println targetsAll[targetenvs]
            println targetsMaster[targetenvs]

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

def defineTargets() {
    println("${JOB_NAME}")
    
    println("${JOB_NAME}".split("/")[0].toLowerCase())

    if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /secure-spil-svc-pipelines/) {
        targetsMaster = targetsMasterSecureSpil
        targetsAll = targetsAllSecureSpil
    }

    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /spil-svc-pipelines/) {
        targetsMaster = targetsMasterSpil
        targetsAll = targetsAllSpil
    }
    /*
    TODO: Ask CTPS SS what targets would Galileo go to?
    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /spilsrl-svc-pipelines/) {
        targetsMaster = targetsMasterMil
        targetsAll = targetsAllMil
    }*/
    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /mil-svc-pipelines/) {
        targetsMaster = targetsMasterMil
        targetsAll = targetsAllMil
    }
    else {
        throw new Exception("No Application Targets found for the defined application")

    }

}

///// The End