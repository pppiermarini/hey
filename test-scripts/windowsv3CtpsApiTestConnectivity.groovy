import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


targetEnv="${target_env}"
testName="myTest"

targets = [
'dev':  ['10.40.2.157']
]

archiveLoc="D\$\\Archive"

emailDistribution="vpilli@incomm.com rkale@incomm.com vhari@incomm.com mpalve@incomm.com"
//General pipeline 


pipeline_id="${env.BUILD_TAG}"

//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"




//currentBuild.result="SUCCESS"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('windows'){
	try { 
			
		cleanWs()
		//select the artifact 
		stage('Generate Test Txt'){
			
			bat"""
			dir
			echo "this is a test file" > test.txt
			dir
			"""

		}

		stage('Deployment'){
			deployComponents(targetEnv, targets[targetEnv])


		}

		
	} catch(exc) {
			currentBuild.result="FAILED"
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
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		deploy(it, envName)
	}
	
}


def deploy(target_hostname, envName) {
	//del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
	//move \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.ear \\\\${target_hostname}\\${archiveLoc}

	echo " the target is: ${target_hostname}"
	bat """
		copy /Y test.txt \\\\${target_hostname}\\${archiveLoc}\\
		"""
}

///// The End
