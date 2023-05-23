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


//globals

emailDistribution="rkale@incomm.com"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))


//Artifact Resolver	input specifics
/*
repository = 'maven-release'
groupId = 'com.incomm.vms'
artifactId = 'vms-event-processing'
env_propertyName = 'ART_VERSION'
targetFileName = 'vms-event-processing-1.0.2-20191011.182459-1.jar'
classifier='exec'
extension = 'jar'
version = '1.0.2-SNAPSHOT'
*/
//artifactVersion="${artifactVersion}"


node('linux'){
	try { 
		cleanWs()

		stage('Artifact check'){
				//artifactResolver([artifact(artifactId: artifactId, classifier: classifier, extension: extension, groupId: groupId, version: env_propertyName)])
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: extension, classifier: classifier, groupId: groupId, version: version, targetFileName: targetFileName)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
				artifactResolver([artifact(artifactId: 'vms-security', groupId: 'com.incomm.vms', version: '1.0.0'), artifact(artifactId: 'vms-event-processing', groupId: 'com.incomm.vms', version: '1.0.2')])
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

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}


} //end of node

///// The End