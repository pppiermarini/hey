import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-dds-mule-dds-batch-poller-mule.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"



emailDistribution="ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com pchattaraj@incomm.com"
//General pipeline 

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"
currentBuild.result="SUCCESS"

node('windows'){
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}

		stage('Setting JDK') {
				jdk=tool name:"jdk1.8.0_202"
				env.JAVA_HOME="${jdk}"
				echo "jdk installation path is: ${jdk}"
				bat "${jdk}\\bin\\java -version"

		}
		
		
		stage('Build'){

			if (userInput == 'Build'){

				/*withSonarQubeEnv('sonar'){
					//bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
					bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
				}*/
				bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml clean deploy -Dmaven.test.failure.ignore=false  -DskipTests"
				
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