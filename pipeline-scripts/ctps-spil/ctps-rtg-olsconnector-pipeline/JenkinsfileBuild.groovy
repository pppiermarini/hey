import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/ctps-rtg-olsconnector.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


//ppiermarini@incomm.com vhari@incomm.com DigitalDeliveryDevs@incomm.com
emailDistribution="DevOps_Engineering@incomm.com vpilli@incomm.com"
//General pipeline 

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"
currentBuild.currentResult="SUCCESS"

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

				withSonarQubeEnv('sonar'){
					bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
				}
				
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
		
		
		/*stage('Quality Gate'){
			
			if ((userInput != 'Release')||(userInput != 'Promote')){

			echo "Quality Gate commented out for this build"
		//		sleep(25) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
		//			timeout(time: 1, unit: 'HOURS') {	

		//			def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
		//				if (qg.status != 'OK') {
		//				error "Pipeline aborted due to quality gate failure: ${qg.status}"
		//				}
		//		echo "after QG timeout"
		//		}
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}*/
		
	} catch(exc) {
			currentBuild.currentResult="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "Build completed"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
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

///// The End