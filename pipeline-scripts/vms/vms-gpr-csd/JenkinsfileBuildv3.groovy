import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _



gitRepository="https://github.com/InComm-Software-Development/vms-csd.git"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


emailDistribution="ppiermarini@incomm.com"
//General pipeline
JAVA_VERSION="${env.JAVA}"
pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"
currentBuild.result="SUCCESS"

snykTokenId = "snyk-vms"
artifactId = ""
artifactVersion = ""

node('linux'){
echo "Java Version is ${JAVA_VERSION}"
jdk=tool name:"${JAVA_VERSION}"
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"

	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		
		stage('Build'){

			if (userInput == 'Build'){
				//sonar  pipelineSonar  sonarqube.incomm.com
			//	withSonarQubeEnv('sonarqube.incomm.com'){
					sh "${maven} clean deploy -f pom.xml -e -U -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

			//	}

			}else if (userInput == 'Release'){

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Dmaven.test.skip=true -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Dmaven.test.skip=true -Darguments='-Dmaven.javadoc.skip=true'" 
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"
				
			}else {
			echo "no build"	
			}

		} //stage
		

		stage('Quality Gate'){

			if (userInput == 'Build'){
				
				//sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
				
				//timeout(time: 3, unit: 'MINUTES') {	
				//	def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
				//		if (qg.status != 'OK') {
			//			error "Pipeline aborted due to quality gate failure: ${qg.status}"
		//				}
				echo "Not running Quality Gate"
				}
				
			else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}

		stage('snyk-scan') {
			pom = readMavenPom file: 'pom.xml'
			artifactId = pom.getArtifactId()
			artifactVersion = pom.getVersion()
			snykScanV2(snykTokenId, artifactId, artifactVersion)
		}

	} catch(exc) {
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


def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End