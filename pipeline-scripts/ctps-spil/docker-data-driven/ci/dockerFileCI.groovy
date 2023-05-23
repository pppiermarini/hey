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
prod_list=""
registry="https://docker.maven.incomm.com"
snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
testdir="reports"
vulnerabilityFound=""
imageLabel=""
labelScan = ""

jfrog = "/usr/bin/jfrog"

node('linux2'){
	try { 
		cleanWs()

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
            if (projectProperties.imageInfo.imageName == null && projectProperties.imageInfo.imageTag == null) {
                throw new Exception("Please add missing imageName or imageTag in datadriven yaml")
            }
        }

        /*
		stage('Scan'){

				//@TODO: Test snyk container dockerfile test

				sh"${snyk} container test ${projectProperties.imageInfo.imageName} --file=Dockerfile --json | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${labelScan}.html"
			
		}//stage
		
		
		stage('Report') {

			dir("${testdir}") {
				sh """
				mv ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${labelScan}.html ${WORKSPACE}/${testdir}
				ls -ltra 
				"""
			}
			
				publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports',
	 			reportFiles: "${projectProperties.imageInfo.imageName}-${labelScan}.html",
	 			reportName: "Report for ${projectProperties.imageInfo.imageName}",
	 			reportTitles: "The Report of ${projectProperties.imageInfo.imageName}"])
		}
		*/

		
		stage('Build-Release'){

			if (userInput == 'Build') {
				if (projectProperties.imageInfo.imageName != 'null') {
					//@TODO: How are we doing versioning/tagging of container? 
					labelScan = "${projectProperties.imageInfo.imageTag}"

					def dockerImage = docker.build("${projectProperties.imageInfo.imageName}:${labelScan}") // @TODO: need to dynamically input image paramters and version, datadriven, pom file or ${env.BUILD_ID}
					docker.withRegistry(registry){
					dockerImage.push()
					}
        
				}

			}
			else {
				echo "Release"
				if (projectProperties.imageInfo.imageName != 'null') {
					allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
					imageLabel = getImageTag(imageLabel, allSnaps)
					prod_list = callDockerRegistryV2(projectProperties.imageInfo.imageName, 'prod')
					echo "Checking existing promoted tags"
					//echo "${projectProperties.imageInfo.imageName}-${imageLabel}-RELEASE"
					for (String tag in prod_list) {
						if (tag == "${imageLabel}-RELEASE") {
							throw new Exception("The image ${projectProperties.imageInfo.imageName}:${imageLabel} already exits in docker-prod and cannot be re-promoted")
						}
					}					
					echo "Promoting ${projectProperties.imageInfo.imageName}..."
					sh """ ${jfrog} rt docker-promote ${projectProperties.imageInfo.imageName} docker-local docker-prod --source-tag="${imageLabel}" --target-tag="${imageLabel}-RELEASE"""
					echo "${projectProperties.imageInfo.imageName} promtoted to docker-prod"

        		}
			}
		}

		stage('Cleanup') {
			if(userInput == "Build") {
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi -f'''
        	}
        	else {
        		echo "It's a Release"
        	}

        }


	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    //Sending a bunch of information via email to the email distro list of participants	

    sendEmailNotificationBuildDocker(projectProperties.email.emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, projectProperties.imageInfo.imageName, labelScan)	
	}
		
}  //end of node

def sendEmailNotificationBuildDocker(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME, def imageName, def imageLabel) {
	if (currentBuild.currentResult == "SUCCESS"){
    emailext attachmentsPattern:  '**/*.html',mimeType: 'text/html', attachLog: true, 
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
				    <li>ImageLabel: ${imageLabel}</li>
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
				    <li>ImageLabel: ${imageLabel}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if (currentBuild.currentResult == "FAILURE"){
    emailext attachmentsPattern:  '**/*.html',mimeType: 'text/html', attachLog: true, 
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
				    <li>ImageLabel: ${imageLabel}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	}
	else{
		
		echo "Issue with System Results please check"
		
	}
}

///// The End
