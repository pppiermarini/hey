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
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk


gitRepository="https://github.com/InComm-Software-Development/fdm-zuul-gateway.git"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


emailDistribution="ppiermarini@incomm.com"
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
		
		
		stage('Build'){

			if (userInput == 'Build'){
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
					bat "${maven} clean compile -f pom.xml -e -U --batch-mode sonar:sonar"
				}

			
			}else if (userInput == 'Release'){

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				bat "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				bat "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				
			}else {
			echo "no build"	
			}


		} //stage
		

		stage('Quality Gate'){

			if (userInput == 'Build'){
				
				sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				
				timeout(time: 3, unit: 'MINUTES') {	
					def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
						if (qg.status != 'OK') {
						error "Pipeline aborted due to quality gate failure: ${qg.status}"
						} else {
							echo "Quality gate passed"
							sh "${maven} package deploy"
						}
				echo "after QG timeout"
				}
				
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
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
