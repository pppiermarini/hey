import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/vms-ms.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

allSnaps = []
// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"
folder="none"

//General pipeline
emailDistribution="vhari@incomm.com"
def serviceSelect=''
artifactId=''

runQualityGate = "${RUN_QUALITY_GATE}"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

artifactExtension='jar'
JAVA_VERSION="${JAVA}"

snykTokenId = "snyk-vms"
artifactId = ""
artifactVersion = ""

node('linux2'){
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
			sh "java -version"
			sh "export JAVA_HOME=/usr/java/default/"
			sh "echo $JAVA_HOME"
		}
		
		// JNOTES: To choose which Microservice to build, it does 'ls' then splits the result
        stage('PrepareServiceList'){
			def my_choices = sh(script: 'ls -d vms*', returnStdout: true).split()

			def wordlist = new ArrayList(Arrays.asList(my_choices))
	
			echo "list of service ${wordlist}"
			serviceSelect = input  message: 'select micro service from below list',ok : 'Build',id :'tag_id',
			parameters:[choice(choices: wordlist, description: 'Select a microservice', name: 'serviceSelect')]
			echo "Selected service : ${serviceSelect}"
		}
		
        stage('Build') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            def artifactId = "${serviceSelect}"
            echo "artifactId : ${artifactId}"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                // JNOTES: uses the 'artifactId' to select the path inside the git repo
                build(userInput, gitBranch, artifactId, artifactExtension)
            }
        }
		

        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }

		stage('snyk-scan') {
			dir("${serviceSelect}") {
				pom = readMavenPom file: 'pom.xml'
				artifactId = pom.getArtifactId()
				artifactVersion = pom.getVersion()
				snykScanV2(snykTokenId, artifactId, artifactVersion)
			}
		}

currentBuild.result = 'SUCCESS'
		
	} catch (any) {

		
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

def build(def userInput, def gitBranch, def artifactId, def artExtension){
  maven = "/opt/apache-maven-3.2.1/bin/mvn"
  if (userInput == 'Build') {
		withSonarQubeEnv('sonarqube.incomm.com'){
			sh "java -version"
			sh "export JAVA_HOME=/usr/java/default/"
			sh "echo $JAVA_HOME"
			sh "${maven} clean deploy -f ${artifactId}/pom.xml -U sonar:sonar -Dmaven.test.skip=true"
		}
	}

	else if (userInput == 'Release') {

		echo "Maven Release Build"
		echo "Maven Release:Prepare..."
		pom = readMavenPom file: 'pom.xml'
		def mavenReleaseVersion = pom.getVersion();
		echo " maven release= " + "${mavenReleaseVersion}"
		sh "${maven} -B -f ${artifactId}/pom.xml org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
		sleep(3)
		sh "${maven} -B -f ${artifactId}/pom.xml org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
		sleep(4)
		def str = mavenReleaseVersion.split('-');
		def myrelease = str[0];
		writeFile file: "mavenrelease", text: myrelease
		//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"

	} 

	else if (userInput == 'Test') {

		echo "Running only tests"
		
	}

	else {
		echo "no build"
	}
}
///// The End