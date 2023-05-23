import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//https://github.com/InComm-Software-Development/ctps-spil-services-target.git
gitRepository="${GIT_REPOSITORY}"
gitBranch="${Branch}"
gitCreds="scm-incomm"
emailDistribution="dstovall@InComm.com ppiermarini@incomm.com"
//userInput = "${BUILD_TYPE}"
//General pipeline

registry="docker.maven.incomm.com"
//apache-maven-3.5.0 -> build-tst
//apache-maven-3.3.9 -> build incomm
maven="/opt/apache-maven-3.2.5/bin/mvn"
snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
scanCodeDir="reports-code"
scanContainerDir="reports-container"
vulnerabilityFound=""
imageLabel=""
labelScan=""
jfrog = "/usr/bin/jfrog"
//currentBuild.result = "SUCCESS"
//master
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
        ///usr/bin/snyk-linux test --json

        stage('Snyk Auth for Ctps') {
        	echo "Snyk auth for ctps"
        	withCredentials([string(credentialsId: 'snyk-ctps', variable: 'AUTH')]) {
				sh "${snyk} auth ${AUTH}"
			}

        }

		stage('Scaning Code and Container'){
				echo "Local scan initiated"
				pom = readMavenPom file: 'pom.xml'
				labelScan = pom.getVersion();
				sh "${maven} -B -f pom.xml -U clean compile jib:buildTar"
				sh "whoami"
				sh "docker load --input ${WORKSPACE}/target/jib-image.tar"
				sh "rm -fR ${WORKSPACE}/target"
				//sh"${snyk} test --json --severity-threshold=low | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${labelScan}-code.html"
				//sh"${snyk} container test --json ${registry}/${projectProperties.imageInfo.imageName}:${labelScan} | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${labelScan}-container.html"
			
		}//stage
		
		/*
		stage('Publishing Reports') {
			/*
			dir("${scanContainerDir}") {
				sh """
				mv ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${labelScan}-container.html ${WORKSPACE}/${scanContainerDir}
				ls -ltra 
				"""
			}*/
			/*
			dir("${scanCodeDir}") {
				sh """
				mv ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${labelScan}-code.html ${WORKSPACE}/${scanCodeDir}
				ls -ltra 
				"""
			}*/
				/*
				publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-container',
	 			reportFiles: "${projectProperties.imageInfo.imageName}-${labelScan}-container.html",
	 			reportName: "Report for ${projectProperties.imageInfo.imageName}-container",
	 			reportTitles: "The Report of ${projectProperties.imageInfo.imageName}-container"])*/
			/*	
	 			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "${projectProperties.imageInfo.imageName}-${labelScan}-code.html",
	 			reportName: "Report for ${projectProperties.imageInfo.imageName}-code",
	 			reportTitles: "The Report of ${projectProperties.imageInfo.imageName}-code"])
		}*/
		//@TODO: Need to setup metrics baring Security and Development thresholds
		/* 
		stage('Checking Vulnerability') {
        	vul_message = sh(script: """/bin/grep "No known vulnerabilities detected." ${WORKSPACE}/${testdir}/${projectProperties.imageInfo.imageName}-${labelScan}.html | wc -l""", returnStdout: true).trim()
        	echo "${vul_message}"
        	if ("${vul_message}" == "1") {
        		echo "No vulnerabilities found! Great work! Proceeding to push the image.."
			}
			else if ("${vul_message}" == "0") {
		        		error("Build failed because vulnerabilities were found, please see the attached email HTML report!")
			}
			else {
				echo "N/A"
			}
        }*/
		
		stage('Build-Release'){

			if (userInput == 'Build') {
				when (projectProperties.imageInfo.imageName != 'null') {

				echo "Build"
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"

				sh "${maven} -B -f pom.xml -U clean compile jib:build"
				}

			}
			else {
				echo "Release"
				when (projectProperties.imageInfo.imageName != 'null') {
					allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
					imageLabel = getImageTag(imageLabel, allSnaps)
					echo "${projectProperties.imageInfo.imageName}:${imageLabel}"
					echo "Promoting ${projectProperties.imageInfo.imageName}..."
					sh """ ${jfrog} rt docker-promote ${projectProperties.imageInfo.imageName} docker-local docker-prod --source-tag="${imageLabel}" --target-tag="${projectProperties.imageInfo.imageName}-${imageLabel}-RELEASE" --copy=true"""
					echo "${projectProperties.imageInfo.imageName} promtoted to docker-prod"
        		}

			}
			
		}

		stage('Cleanup') {
			
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi -f'''

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
    //echo "${currentBuild.result}"
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