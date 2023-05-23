import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

userInput="Promote"
targetEnv="${target_env}"

testName="myTest"

//targets = [
//	'prod': ['10.41.7.110','10.41.7.111','10.41.7.112','10.41.7.113','10.41.7.114','10.41.7.115'],
//]

targets = [
		'prod_spltmsfsapp01fv': ['10.41.7.110'],
		'prod_spltmsfsapp02fv': ['10.41.7.111'],
		'prod_spltmsfsapp03fv': ['10.41.7.112'],
		'prod_spltmsfsapp04fv': ['10.41.7.113'],
		'prod_spltmsfsapp05fv': ['10.41.7.114'],
		'prod_spltmsfsapp06fv': ['10.41.7.115'],
		'dev_sdtmsapp01v': ['10.42.18.207'],
]


//emailDistribution="dstovall@incomm.com ppiermarini@incom.com smohammad@incomm.com schahanapally@incomm.com"
emailDistribution="schahanapally@incomm.com"

artifactDeploymentLoc = "/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/webapps"
libDeploymentLoc="/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/lib"
instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/logs"
serviceName="tms-fs-instance"
pipeline_id="${env.BUILD_TAG}"
def artifactloc = "${env.WORKSPACE}"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.services.tmsfs'
artifactId = 'tms-fs-service'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'tms-fs-service.war'

artifactRealName = 'tms-fs-service'

def approver = ''
def approval_status = '' 
def operators = []

//globals
relVersion="null"

myenv = ""
star = "*"
prop = "*.properties"
yml = "*.yml"
xml = "*.xml"

user = "tcserver"
group = "pivotal"

gitRepository = "https://github.com/InComm-Software-Development/cfes-tmsfs-fraudmonitoring.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"
credsName = "scm_deployment"

approvalData = [
		'operators': "[awadhwa,amohammad,schahanapally,nprasobhan,dstovall]",
		'adUserOrGroup' : 'awadhwa,amohammad,schahanapally,nprasobhan,dstovall',
		'target_env' : "${targetEnv}"
]


node('linux') {
	try {

		echo "Following build parameters selected: "
		echo "BUILD_TYPE - ${userInput}"
		echo "BRANCH - ${gitBranch}"
		echo "target_env - ${targetEnv}"

		String[] ENV;
		ENV = targetEnv.split('_');
		myenv = "${ENV[0]}"

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout') {
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		//select the artifact 
//		stage('Approval') {
//
//			echo "Inside approval block"
//			operators= "['awadha', 'dstovall', 'schahanapally', 'smohammad']"
//			def Deployment_approval = input message: 'Deploying to PROD', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
//			echo "${Deployment_approval}"
//			approval_status = "${Deployment_approval['approval_status']}"
//			def operator = "${Deployment_approval['approver']}"
//			String op = operator.toString()
//
//			if (approval_status == 'Proceed') {
//				echo "Operator is ${operator}"
//				if (operators.contains(op))
//				{
//					echo "${operator} is allowed to deploy into ${targetEnv}"
//				}
//				else
//				{
//					throw new Exception("Throw to stop pipeline as user not in approval list")
//				}
//			} else {
//				throw new Exception("Throw to stop pipeline as user selected abort")
//			}
//        }

		stage('Get Artifact') {

			if (userInput == 'Promote') {
				// Select and download artifact
				list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
				echo "the list contents - ${list}"
				relVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
						parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
				sleep(3)
				artifactWget(repoId, groupId, artifactId, artExtension, relVersion)
				echo "the artifact version - ${relVersion}"
				sh "ls -ltr"
			}
		}

//        stage ('Get Artifact') {
//
//			// Select and download artifact
//            list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
//            echo "the list contents ${list}"
//            relVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
//            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
//            sleep(3)
//            artifactWget(repoId, groupId, artifactId, artExtension, relVersion)
//            echo "the artifact version ${relVersion}"
//    		sh "ls -ltr"
//    	}

		stage("Deployment to ${targetEnv}") {

			echo "deploying this file:  ${artifactId}-${relVersion}.${artExtension}"

			if (userInput == 'Promote') {
				sh"cp ${artifactId}-${relVersion}.${artExtension} ${artifactName}"
				sh"ls -ltr"
				deployComponents(targetEnv, targets[targetEnv],"${artifactName}")
			} else {
				echo "not deploying during a release build"
			}

		}

	} catch (exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		notifyBuild(emailDistribution)
		//sendEmail(emailDistribution, userInput, gitBranch) 
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def deployComponents(envName, targets, Artifact){

	echo "Deploying to - ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
}

def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo "the target is: ${target_hostname}"
	//echo "quitting..."

	sshagent([credsName]) {
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'
		scp -q -o StrictHostKeyChecking=no ${artifactRealName}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${myenv}/${star} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${prop} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${xml} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
	"""
	}
}

def getFromArtifactory() {
	if (userInput == "Promote") {
		echo "Select an artifact from Artifacory"

		relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)

		echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
	} else {
		echo "not promoting-Skipping"
	}
}

/*
def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'
		scp -q -o StrictHostKeyChecking=no ${artifactRealName}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${target_env}/${star} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${prop} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${xml} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
	"""
	def r = readFile('commandresult').trim()
			echo "arr= p${r}p"
			if(r == "1"){
			echo "failed deployment"
			currentBuild.result = 'FAILED'
			} else {
			echo "checking for deployed"
			}
			try {		
			timeout(1) {
				waitUntil {
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactName}*.deployed | wc -l' > commandresult"""
						def a = readFile('commandresult').trim()
						echo "arr= p${a}p"
						if (a == "1"){
						return true;
						}else{
						return false;
						}
				   }
				   
				}
		} catch(exception){
			echo "${artifactName} did NOT deploy properly. Please investigate"
			abortMessage="${artifactName} did NOT deploy properly. Please investigate"
			currentBuild.result = 'FAILED'
		}
	//}else{
     //   echo "Deployed to Contingency Server"
    }

def getFromArtifactory(){
	// prompts user during stage
	getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"

}
*/

def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def tests(target, envName, testName){
	
	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
	sleep(1)
		dir('testresults'){
			//println "Run Test Script"
			//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
			// String results = readFile 'testresults.xml'
		}
		//if(1 ){
		//	println "ERROR todo "
		//} else {
		//	println "results"
		//}
}

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End