import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-dds-microservices-dds-realtime-processor.git"
//gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


//DigitalDeliveryDevs@incomm.com
emailDistribution="ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com DigitalDeliveryDevs@incomm.com"
//General pipeline 

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"
currentBuild.result="SUCCESS"

node('windows'){
	try { 


		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			bat "${jdk}\\bin\\java -version"
		}

		stage('checkout'){
			cleanWs()
			//listGithubBranches(gitCreds,gitRepository)

			git credentialsId: "${gitCreds}", url: "${gitRepository}"
			bat 'git branch -r >> branch.txt'
			listBranches = readFile 'branch.txt'
			echo "please click on the link here to choose a branch"
			env.BRANCH_SCOPE = input message: 'Please choose the branch to build ', ok: 'Ok',parameters: [choice(name: 'BRANCH_NAME', choices: "${listBranches}", description: 'Branch to build?')]
	
			echo "Checking out p${env.BRANCH_SCOPE}p"
			bat "git branch"
			cleanWs()
		}
		
		stage('Github Checkout') {
			cleanWs()
			//echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,"${env.BRANCH_SCOPE}")

		}

		
		stage('Build'){

			if (userInput == 'Build'){

				/*withSonarQubeEnv('sonar'){
					//bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar"
					bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
				}*/
				bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests"
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

def notifyBuild(recipients) {
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}

///// The End
