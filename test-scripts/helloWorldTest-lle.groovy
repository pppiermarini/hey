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
gitBranch="origin/docker-jib" //maven-tst docker-jib
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
repo="incomm-snapshot"
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
					
					sh "${maven} --settings /home/tcserver/.m2/settings-tst.xml clean deploy -f pom.xml -DskipTests"
					

					
					sh "${maven} --settings /home/tcserver/.m2/settings-tst.xml -B -f pom.xml -U clean compile jib:build"
					
					
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
		

		} //stage
	
		stage('Artifact Resolver Test') {
			echo "Hello-world artifact resolver plugin test"
			artifactResolver artifacts: [artifact(artifactId: artId, extension: aExt, failOnError: true, groupId: gId, version: "${Artifact}")], enableRepositoryLogging: true, repositoryId: 'maven-tst-snapshot'

		}

		stage('Artifact Resolver Library Test') {
			echo "Initiating a Hello world artifact library test"

			list = artResolverlle(repo,gId, artId, aExt)


			artifactVer = input message: 'Select 0.0.37-SNAPSHOT',ok : 'Deploy',id :'tag_id',
				parameters:[choice(choices: list, description: 'Select 0.0.37-SNAPSHOT', name: 'VERSION')]

			artifactWgetlle(repo, gId, artId, aExt, artifactVer)
		}

		stage('Docker Artifact API Tag Retrival Test') {
			echo "Hello world docker latest tag pulls"
			echo "Choose the latest tag from dropdown"
			allSnaps = callDockerRegistrylle("hello-world")
			imageLabel = getImageTaglle(imageLabel, allSnaps)

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

def artResolverlle(repo,groupId,artifactId, artifactExtension) {
	echo "${repo}"
	echo "${groupId}"
	echo "${artifactId}"
	echo "${artifactExtension}"

	params = []

	withCredentials([usernamePassword(credentialsId: 'maven-tst-jenkinstest', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
		data = sh (
		  script: "curl -u ${USERNAME}:\"${PASSWORD}\" \"https://maven-tst.incomm.com/artifactory/api/search/versions?g=${groupId}&a=${artifactId}&repos=${repo}\"",
		  returnStdout: true
		).trim()
	}

	def jsonObj = readJSON text: data
	 artifactVersions = []
	jsonObj.results.each { record ->
	artifactVersions << record.version
	}

return artifactVersions;
}


def artifactWgetlle(repo,groupId,artifactId, artifactExtension,artifactVersion){

	/*
		def groupIdDir = ""
		groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1
		echo "${groupIdDir}"
	*/

	withCredentials([usernamePassword(credentialsId: 'maven-tst-jenkinstest', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
	sh """
	
		wget --user "${USERNAME}" --password "${PASSWORD}" --no-check-cert --no-verbose https://maven-tst.incomm.com/artifactory/${repo}/${groupId}/${artifactId}/${artifactVersion}/${artifactId}-${artifactVersion}.${artifactExtension} || exit 1
	"""
	}
}


def callDockerRegistrylle(imageName) {
	allSnaps = [] 
	echo "${imageName}"
	
	final String call = "https://docker.maven-tst.incomm.com/artifactory/api/docker/docker-local/v2/${imageName}/tags/list"
	
	withCredentials([usernamePassword(credentialsId: 'maven-tst-jenkinstest', passwordVariable: 'PASS', usernameVariable: 'USER')]) {

			response = sh(script: "curl -X GET ${call} -u ${USER}:${PASS}", returnStdout: true).trim()
			def dataApi = readJSON text: response
			
			for (i in dataApi.tags) {
				allSnaps.add(i)
			}
		}
	echo "${allSnaps}"

	return allSnaps
}


def getImageTaglle(imageLabel, imageSnaps) {
			try {
			timeout(time: 90, unit: 'SECONDS'){
                    imageLabel = input  message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
                    parameters:[choice(choices: allSnaps, description: 'Select a tag for this build', name: 'TAG')]
                	}

    				echo "${imageLabel}"
		}
		catch (err) {
			echo "Build Aborted"
			currentBuild.result = "ABORTED"
		}
	return imageLabel
}
///// The End