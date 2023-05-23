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



gitRepository="https://github.com/InComm-Software-Development/hello-world.git"
gitBranch="origin/docker-jib-hle" //build-tst ->docker-jib
gitCreds="scm-incomm"

// inputs from build with parameters
testName="myTest"

//General pipeline
emailDistribution="rkale@incomm.com"


pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

artId = "hello-world"
gId = "hello-world-testing"
ver ="${Artifact}"
rId = "maven-all"
aExt = "jar"
ArtifactVersion = ""
list = ""
allSnaps = ""
imageLabel = ""

node('linux'){
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		cleanWs()

		stage('Setting JDK') {
			jdk=tool name:"openjdk-11.0.5.10"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}

		stage('checkout'){
			
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		
		stage('Build'){

			
				//sonar  pipelineSonar  sonarqube.incomm.com
				
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar"
					
					sh "${maven} clean deploy -f pom.xml -DskipTests"
					

					
					sh "${maven} -B -f pom.xml -U clean compile jib:build"
					
					
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
		

		} //stage
	
		stage('Artifact Resolver Test') {
			echo "Hello-world artifact resolver plugin test"
			artifactResolver artifacts: [artifact(artifactId: artId, extension: aExt, failOnError: true, groupId: gId, version: "${Artifact}")], enableRepositoryLogging: true, repositoryId: 'maven-all'

		}

		stage('Artifact Resolver Library Test') {
			echo "Initiating a Hello world artifact library test"

			list = artifactResolverV2(rId, gId, artId, aExt)


			artifactVersion = input message: 'Select 0.0.37-SNAPSHOT',ok : 'Deploy',id :'tag_id',
				parameters:[choice(choices: list, description: 'Select 0.0.37-SNAPSHOT', name: 'VERSION')]

			artifactWget(rId, gId, artId, aExt, artifactVersion)
		}

		stage('Docker Artifact API Tag Retrival Test') {
			echo "Hello world docker latest tag pulls"
			echo "Choose the latest tag from dropdown"
			allSnaps = callDockerRegistry("hello-world")
			imageLabel = getImageTag(imageLabel, allSnaps)

			echo "${allSnaps}"
			
			echo "${imageLabel}"
			


		}


currentBuild.result = 'SUCCESS'
		
	} catch (any) {
		echo "Muy Mal"
		
	} finally {
		
		if (currentBuild.result == "SUCCESS"){
			
			stage('Notification'){
				currentBuild.result = 'SUCCESS'
				sendEmail(emailDistribution, gitBranch, 'Build' )	
			}
			
		}else{
			
			stage('Notification'){
				currentBuild.result = 'FAILURE'
				sendEmail(emailDistribution, gitBranch, 'Build' )
				echo 'ERROR:  '+ exc.toString()
				//throw exc
			}
		}
	}
	

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



///// The End