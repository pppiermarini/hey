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


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-project-appleRestApi.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
//folder="ear"


emailDistribution="vpilli@incomm.com rkale@incomm.com vhari@incomm.com mpalve@incomm.com psubramanian@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"



node('linux'){
	try { 

		stage('Build'){

			if (userInput == 'Build'){
				cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonar'){
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar"
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=origin/MojohausTest"

					sh "${maven} clean deploy -f pom.xml -DskipTests sonar:sonar -Dsonar.branch.name=${Branch}"
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
				}
			
			}else if (userInput == 'Release'){
				cleanWs()
				gitBranch="origin/MojohausTest"
			githubCheckout(gitCreds,gitRepository,gitBranch)
		
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				//-Darguments='-Dmaven.javadoc.skip=true' -Dmaven.test.skip=testResults
				//@TODO: ${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true -DupdateWorkingCopyVersions=false' org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare
				//@TODO: ${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true -DupdateWorkingCopyVersions=false' org.apache.maven.plugins:maven-release-plugin:2.5.3:perform
				//sh "${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true' org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				
				sh "${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true' -B clean build-helper:parse-version org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -DdevelopmentVersion=${mavenReleaseVersion}"

				//sh "${maven} versions:use-releases"
				prop = readProperties file: 'release.properties'
				echo "${prop}"
				//def tagVersion = prop['scm.tag'];
				//echo "tagVersion = ${tagVersion}"
				//sleep(3)
				//sh "${maven} -B -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true' org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				//sh "${maven} versions:set -DnewVersion=${mavenReleaseVersion} -DgroupId=* -DartifactId=*"
				//sh "${maven} -B release:update-versions -DdevelopmentVersion=${mavenReleaseVersion}"
				//sh "${maven} -B -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true' org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"

				//sh "git add pom.xml && git add ear/pom.xml && git add ejb/pom.xml && git commit -m 'adding the updated poms' && git push origin ${gitBranch}"

				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"
				
			}else {
			echo "no build"	
			}

		} //stage
		

		/*stage('Quality Gate'){
			
			//sonarProjectName="hello-world-testing:hello-world"

			if (userInput == 'Build'){
				
				sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
				
				timeout(time: 1, unit: 'HOURS') {	
					def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
						if (qg.status != 'OK') {
						error "Pipeline aborted due to quality gate failure: ${qg.status}"
						}

						echo "Quality gate passed"
						sh "${maven} package deploy"
				echo "after QG timeout"
				}
				
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}*/
		
	} catch (any) {
		echo "Muy Mal"
		currentBuild.currentResult = 'FAILURE'

	} finally {
	
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
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
