import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository="${GIT_REPOSITORY}"
gitBranch="${Branch}"
gitCreds="scm-incomm"
emailDistribution=""
//General pipeline

registry="docker.maven.incomm.com"
maven="/opt/apache-maven-3.6.3/bin/mvn"
snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
scanCodeDir="reports-code"
scanContainerDir="reports-container"
vulnerabilityFound=""
imageLabel=""
labelScan=""
jfrog = "/usr/bin/jfrog"
prod_list=""
snykTokenId = "snyk-ctps"


node('linux2'){
	try { 
		cleanWs()

		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}

		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}//stage

		stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'dataDrivenDocker.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }

            echo "Sanity Check"
            if (projectProperties.imageInfo.imageName == null) {
                throw new Exception("Please add the image name")
            }
        }
		
		stage('Build-Release'){

			if (userInput == 'Build') {
				when (projectProperties.imageInfo.imageName != 'null') {

				echo "Build"
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				//mvn 
				sh "${maven} -B -f pom.xml -U -X clean compile jib:build"
				}

			}
			else {
				echo "Release"
				when (projectProperties.imageInfo.imageName != 'null') {
					allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
					if (allSnaps.isEmpty()) {
							throw new Exception("The image ${projectProperties.imageInfo.imageName} does not have any tags in docker-local registry please increment the tag number and run userInput as Build")
					}
					imageLabel = getImageTag(imageLabel, allSnaps)
					prod_list = callDockerRegistryV2(projectProperties.imageInfo.imageName, 'prod')
					echo "Checking existing promoted tags"
					for (String tag in prod_list) {
						if (tag == "${imageLabel}-RELEASE") {
							throw new Exception("The image ${projectProperties.imageInfo.imageName}:${imageLabel} already exits in docker-prod and cannot be re-promoted")
						}
					}	
					echo "${projectProperties.imageInfo.imageName}:${imageLabel}"
					echo "Promoting ${projectProperties.imageInfo.imageName}..."
					sh """ ${jfrog} rt docker-promote ${projectProperties.imageInfo.imageName} docker-local docker-prod --source-tag="${imageLabel}" --target-tag="${imageLabel}-RELEASE" """
					echo "${projectProperties.imageInfo.imageName} promtoted to docker-prod"
        		}

			}
			
		}

		stage('snyk-scan') {
			pom = readMavenPom file: 'pom.xml'
			artifactId = pom.getArtifactId()
			artifactVersion = pom.getVersion()
			snykScanV2(snykTokenId, artifactId, artifactVersion)
		}

		stage('snyk-container-scan') {
			pom = readMavenPom file: 'pom.xml'
			imageName = pom.getArtifactId()
			imageTag = pom.getVersion()
			snykContainerScan(snykTokenId, imageName, imageTag)
		}

		//Enable after Snyk container scan POJ is budgeted and aquired
		/*
		stage('Cleanup') {
			
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi -f'''

        }*/


	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
		echo "notify"
      sendEmailNotificationBuildDocker(projectProperties.email.emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, projectProperties.imageInfo.imageName, labelScan)	
	}
		
}  //end of node

def sendEmailNotificationBuildDocker(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME, def imageName, def labelScan) {
	echo "${emailDistribution}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
    emailext attachmentsPattern:  '**/*.html',
    mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
				    <li>ImageName: ${imageName}</li>
				    <li>ImageLabel: ${labelScan}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>

				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.currentResult == "ABORTED"){
		echo "aborted"
    emailext attachmentsPattern:  '**/*.html', mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
				    <li>ImageName: ${imageName}</li>
				    <li>ImageLabel: ${labelScan}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
		echo "${emailDistribution}"
    emailext attachmentsPattern:  '**/*.html',
	mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
				    <li>ImageName: ${imageName}</li>
				    <li>ImageLabel: ${labelScan}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	}
}

///// The End
