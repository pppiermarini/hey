import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/FSWEB-GprService-BE.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${userInput}"
testName="myTest"

targetEnv="${target_env}"

targets = [
	'dev':  ['10.42.19.242', '10.42.19.243']
]


emailDistribution="ppiermarini@incomm.com"


//General pipeline 
artifactDeployLoc = "/opt/apps/"

serviceName = ""

//Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.services.tmsfs.fraud'
artifactId ='tmsfs-fraud-service'
env_propertyName ='ART_VERSION'
artExtension ='war'
artifactName ='tmsfs-fraud-service.war'

artifactRealName = 'tmsfs-fraud-service'

//globals
test_suite = "none"
user = "tcserver"
group = "pivotal"
filePermission = "644"
folderPermission = "755"


maven = "/opt/apache-maven-3.2.1/bin/mvn"
currentBuild.result="SUCCESS"

node('linux'){
	
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}


		stage('Build'){

			if ((userInput == 'Build')||(userInput == 'Build-Deploy')){

				//withSonarQubeEnv('sonarqube.incomm.com'){
				sh "${maven} clean package -DskipTests"

				//}

				
			}else if (userInput == 'Release'){
				
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				bat "${maven} -X -Darguments=-DskipTests org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				bat "${maven} -X -Darguments=-DskipTests org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//bat "copy /Y mavenrelease $pipelineData\\DEV"
			}else {
			echo "no build"	
			}

		} //stage


		//if (userInput == 'Build-Deploy'){
			stage('Deploy'){

			//deployComponents(targetEnv, targets[targetEnv])

			}	
		//}

	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
		if (currentBuild.currentResult  == "FAILURE"){
			
			emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		}else if(currentBuild.currentResult  == "SUCCESS"){
		echo "${currentBuild.currentResult}"
		emailext mimeType: 'text/html', attachLog: true, 
			to: "${emailDistribution}",
			subject: "Build job: ${JOB_NAME}", 
					body: 
					"""
					<html>
							<p>**************************************************</p>
					<ul>
						<li>STATUS: ${currentBuild.currentResult}</li>
						<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
						<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					</ul>
							<p>**************************************************</p>\n\n\n
					</html>
					"""	
			//echo "if success"
			//emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		}else{
			
			echo "LAST"
			
		}
}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	
	targets.each {
		println "Item: $it"
		deploy(it, artifactDeployLoc, envName)
	}
	
}

def deploy(target_hostname, artifactDeployLoc, envName) {

	echo " the target is: ${target_hostname}"
	bat """	
		dir \\\\${target_hostname}\\${artifactDeployLoc}\\
		powershell Get-Service -ComputerName ${target_hostname} -Name ${serviceName} ^| Stop-Service
		robocopy /s /e distribution\\ \\\\${target_hostname}\\${artifactDeployLoc}\\
		set ERRORLEVEL=0
		powershell Get-Service -ComputerName ${target_hostname} -Name ${serviceName} ^| Stop-Service
		robocopy /s /e \\\\${target_hostname}\\${artifactDeployLoc}\\ \\\\${target_hostname}\\${artifactLiveLoc}\\
		set ERRORLEVEL=0
		powershell Get-Service -ComputerName ${target_hostname} -Name ${serviceName} ^| Start-Service
	"""
}


def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

///// The End