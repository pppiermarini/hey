import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

gitRepository="https://github.com/InComm-Software-Development/vms-ms-integration-test"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"
credsName = "scm_deployment"

// inputs from build with parameters
userInput="${Input}"
testName="myTest"

emailDistribution="ppiermarini@incomm.com amikhaleu@incomm.com"
//General pipeline

VMS_QAB="10.42.81.17"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.6.3/bin/mvn"
currentBuild.result="SUCCESS"
//runQualityGate = "${RUN_QUALITY_GATE}"
//runSonar = "${RUN_SONAR}"
//JAVA_VERSION="${JAVA}"



node('QAAutomation'){

jdk=tool name:"openjdk-11.0.7.10-0"
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"

	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			//listGithubBranches(gitCreds,gitRepository)
			//echo "Checking out p${env.BRANCH_SCOPE}p"
			//cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}
		
		
		stage('Build'){
        		if (userInput == 'Build'){

				sh """
				${maven} -version
				${maven} clean compile -f pom.xml -e -U -DskipTests
				"""


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

		stage('Test') {
 
		   withCredentials([string(credentialsId: 'vms-integration-test', variable: 'VMSINTKEY')]) {
				sh"""
					${maven} test -DSECURITY_KEY=${VMSINTKEY}
				"""
		   }
		}	
		//mvn test -DDB_USER=FS_QA -DDB_PASSWORD=fsqab123 -DAUTH_USER=CMSAuth -DAUTH_PASSWORD=Password1
		//#${maven} test -pl vms-integration-test -DDB_USER=${USER} -DDB_PASSWORD=${PASS}
		//#${maven} test -DDB_USER=${USER} -DDB_PASSWORD=${PASS} -DAUTH_USER=CMSAuth -DAUTH_PASSWORD=${PASS}

	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		sendEmailv3(emailDistribution, getBuildUserv1())
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
