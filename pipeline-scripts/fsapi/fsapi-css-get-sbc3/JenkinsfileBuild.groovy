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


gitRepository="https://github.com/InComm-Software-Development/FSAPI-CSS-SBC3.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
folder="CSS_Get"


emailDistribution="ppiermarini@incomm.com ssanka@incomm.com dkumar@incomm.com"
//General pipeline


serviceName="cssget"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"



node('linux'){
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
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar"
					sh "${maven} clean deploy -f ${folder}/pom.xml -DskipTests"
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
				}
			
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
		

		stage('Quality Gate'){
			
			//sonarProjectName="hello-world-testing:hello-world"

			if (userInput == 'Build'){
				
			//	sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				
			//	timeout(time: 1, unit: 'HOURS') {	
			//		def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
			//			if (qg.status != 'OK') {
			//			error "Pipeline aborted due to quality gate failure: ${qg.status}"
			//			}
			//	echo "after QG timeout"
			//	}
				
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}

currentBuild.result = 'SUCCESS'
		
	} catch (any) {
		echo "Muy Mal"
		
	} finally {
		
		if (currentBuild.result == "SUCCESS"){
			
			stage('Notification'){
				currentBuild.result = 'SUCCESS'
				sendEmail(emailDistribution, gitBranch, userInput )	
			}
			
		}else{
			
			stage('Notification'){
				currentBuild.result = 'FAILURE'
				sendEmail(emailDistribution, gitBranch, userInput )
				//echo 'ERROR:  '+ exc.toString()
				//throw exc
			}
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

def tests(target, envName, testName){
	
	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
	sleep(1)
		dir('testresults'){
			//println "Run Test Script"
			//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
			// String results = readFile 'testresults.xml'
		}
		//if(1 ){
		//	println "ERROR todo "
		//} else {
		//	println "results"
		//}
}


///// The End
