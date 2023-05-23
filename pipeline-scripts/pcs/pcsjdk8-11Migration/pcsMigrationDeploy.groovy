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

credsName = "scm_deployment"
userInput="Promote"
//targetEnv="${targetEnv}"
//targetEnv="${target_Env}"
testName="myTest"


//Dev:10.42.16.111,10.42.18.143,10.42.18.142,10.42.18.141
//Test:10.44.0.231,10.44.0.165,10.44.0.164,10.44.0.163
//INTG:10.42.50.120,10.42.50.121,10.42.50.111,10.42.50.112,10.42.50.113,10.42.50.101,10.42.50.102,10.42.50.103,10.42.50.108,10.42.50.109,10.42.50.110,10.42.50.98,10.42.50.99,10.42.50.100,10.42.50.105,10.42.50.106,10.42.50.107,10.42.50.95,10.42.50.96,10.42.50.97
//QA:10.42.81.98,10.42.83.68,10.42.83.67,10.42.83.66

targets = [
    'dev':  ['10.42.16.111'],
    'test': []

]


//General pipeline
emailDistribution="flam@incomm.com ahundal@incomm.com"

//Ask Dev
tempDeployLoc="jdkVersions"

pipeline_id="${env.BUILD_TAG}"

//chmod="755"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

currentBuild.result = 'SUCCESS'

jdkElevenBinary="openjdk-11.0.5.10.linux.tar.xz"

jdkUnatar="java-11-openjdk-11.0.5.10-1.static.jdk.openjdkportable.x86_64"

jdkElevenRenamed="openjdk-11.0.5"

node('linux'){
	try { 
		cleanWs()

		stage('checkout'){
			sh """
			/bin/wget --no-check-cert https://maven.incomm.com/artifactory/scm/openjdk/${jdkElevenBinary}
			"""
		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv])
			
			} else {
				echo "not deploying during a release build"
			}

		}
		

		
	} catch (exc) {

			currentBuild.result = 'FAILURE'
			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "Something went wrong check build logs"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node




def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName) } ]
	}
	parallel stepsInParallel
	
}


/*
Algorithm: From Amanpreet
stop the apps, then run
yum remove java-1.8.0-openjdk java-1.8.0-openjdk-devel 
to remove old jdk version
then run
yum install java-11-openjdk java-11-openjdk-devel
/bin/tar -xf ${jdkElevenBinary} 
/bin/mv ${jdkElevenBinary} ${jdkElevenRenamed}

*/

def deploy(target_hostname, envName) {

	echo " the target is: ${target_hostname}"
	echo " the environment is: ${envName}"
	sshagent([credsName]) {
		sh"""
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${tempDeployLoc}'
		scp -q -o StrictHostKeyChecking=no ${jdkElevenBinary} root@${target_hostname}:${tempDeployLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -ltr ${tempDeployLoc}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/tar -xf ${tempDeployLoc}/${jdkElevenBinary}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -ltr ${tempDeployLoc}'
		"""
	}

}

///// The End