import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-web-microservices-incomm-digital-sendemail-api.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


emailDistribution="ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com DigitalDeliveryDevs@incomm.com"
//General pipeline
artifactDeploymentLoc ="/var/opt/incomm-digital-sendemail-api" 
propertyDeploymentPath=""

serviceName="incomm-digital-sendemail-api" 


pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"


///Artifact Resolve input specifics
repoId = 'maven-release'
groupId = 'com.incomm.dds'
artifactId = 'incomm-digital-sendemail-api'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''


//globals
userApprove="Welcome to the new Jenkinsfile"
envInput="null"
relVersion="null"
sonarStatus="null"
serviceStatus="null"

currentBuild.result="SUCCESS"


node('linux'){
	try { 

		cleanWs()

		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}
		
		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}
		
		stage('Build'){

			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar"
					if (UnitTest == 'Y') {
					sh "${maven} clean deploy -f pom.xml sonar:sonar -Dsonar.branch.name=${BRANCH}"
					}
					else { sh "${maven} clean deploy -f pom.xml sonar:sonar -DskipTests -Dsonar.branch.name=${BRANCH}" }
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
				}
			emailDistribution="vhari@incomm.com rkale@incomm.com DigitalDeliveryDevs@incomm.com"

			
			}else if (userInput == 'Release'){

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"
				
			}else {
			echo "no build"	
			}

		} //stage
		

		/*stage('Quality Gate'){

			if (userInput == 'Build'){
				
				//sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
				
				//timeout(time: 3, unit: 'MINUTES') {	
				//	def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
				//		if (qg.status != 'OK') {
				//		error "Pipeline aborted due to quality gate failure: ${qg.status}"
				//		}
				//echo "after QG timeout"
				//}
				
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}*/


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


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def notifyBuild(recipients) {
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End
