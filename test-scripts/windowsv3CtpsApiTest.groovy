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

currentBuild.result = "SUCCESS"
emailDistribution="rkale@incomm.com "

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))


///Artifact Resolver	input specifics

//groupId = 'com.incomm.spl.project'
//artifactId = 'appleRestApi'

//  <groupId>com.incomm.spl.project.apple.ear</groupId>
//    <artifactId>spl-appleRest</artifactId>

repoId = 'maven-release'
groupId = 'com.incomm.spl.project.apple.ear'
artifactId = 'spl-appleRest'
env_propertyName = 'ART_VERSION'
artExtension = 'ear'
artifactName = ''
artifactBareName = ''
artifactVersion="${artifactVersion}"



gitRepository="https://github.com/InComm-Software-Development/ctps-spil-apple-jboss-configs.git"
gitBranch="${Git_Configuration}"
gitCreds="scm-incomm"

gitCommon="common\\jboss-eap-modules"
node('windows'){
	try { 
		cleanWs()
		//select the artifact
		stage('Testing Windows v3 Script'){
			githubCheckout(gitCreds,gitRepository,gitBranch)

			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
			bat"""
			dir ${gitCommon}
			"""
				
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