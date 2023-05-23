import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//https://github.com/InComm-Software-Development/ctps-spil-services-vix.git
gitRepository="${GIT_REPOSITORY}"
gitBranch="${Branch}"
gitCreds="scm-incomm"
emailDistribution="rkale@incomm.com vhari@incomm.com dstovall@InComm.com ppiermarini@incomm.com"
//userInput = "${BUILD_TYPE}"
//General pipeline

registry="docker.maven.incomm.com"
//apache-maven-3.5.0 -> build-tst
//apache-maven-3.3.9 -> build incomm
maven="/opt/apache-maven-3.5.0/bin/mvn"
snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html"
testdir="reports"
imageLabel="latest"
//master
node('linux'){
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
            projectProperties = readYaml (file: 'test-yamls/dataDrivenDocker.yml')
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
        //--insecure 
		stage('Scan'){
				echo "Local scan initiated"
				dockerPull(registry, projectProperties.imageInfo.imageName, imageLabel)
				
				sh"""
				${snyk} container test --json ${registry}/${projectProperties.imageInfo.imageName}:${imageLabel} | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${imageLabel}.html
				"""
				//sh(script: " ", returnStdout: true).trim()
				//sh ""
				sh """
				mkdir ${WORKSPACE}/${testdir}
				mv ${projectProperties.imageInfo.imageName}-${imageLabel}.html ${WORKSPACE}/${testdir}
				ls -ltra
				"""
		}

		stage('Report') {
		publishHTML (target : [allowMissing: false,
 			alwaysLinkToLastBuild: true,
 			keepAll: true,
 			reportDir: 'reports',
 			reportFiles: "${projectProperties.imageInfo.imageName}-${imageLabel}.html",
 			reportName: "Report for ${projectProperties.imageInfo.imageName}",
 			reportTitles: "The Report of ${projectProperties.imageInfo.imageName}"])
		}
		//stage
		/*
		stage('Build-Release'){

			if (userInput == 'Build') {

				echo "Build"

				sh "${maven} -B -f pom.xml -U -X clean compile jib:build -Dimage=${registry}/${projectProperties.imageInfo.imageName}:${BUILD_NUMBER}"

			}
			else {
				echo "Release"

				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"

				sh "${maven} -B -f pom.xml -U -X clean compile jib:build -Dimage=${registry}/${projectProperties.imageInfo.imageName}:${BUILD_NUMBER}-${mavenReleaseVersion}"

				echo "Initiating the GitHub Tag"
				sh "git tag ${BUILD_NUMBER}-${mavenReleaseVersion} && git push origin ${BUILD_NUMBER}-${mavenReleaseVersion}"


			}
			
		}*/

		stage('Cleanup') {
			
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi -f'''

        }


        stage('Checking Vulnerability') {
        	//error("Build failed because of this and that..")

        	//vuls = sh(script: """'grep '0 known vulnerabilities' ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${imageLabel}.html | wc -l'""", returnStdout: true).trim()
        	//vuls_dep_paths = sh(script: """'grep '0 vulnerable dependency paths' ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${imageLabel}.html | wc -l'""", returnStdout: true).trim()
        	//dependencies = sh(script: """'grep '0 dependencies' ${WORKSPACE}/${projectProperties.imageInfo.imageName}-${imageLabel}.html | wc -l'""", returnStdout: true).trim()
        	vul_message = sh(script: """/bin/grep -iwc "No known vulnerabilities detected." ${WORKSPACE}/${testdir}/${projectProperties.imageInfo.imageName}-${imageLabel}.html | wc -l""", returnStdout: true).trim()
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
        }		

	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    sendEmailNotificationDocker(projectProperties.email.emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, projectProperties.imageInfo.imageName, imageLabel)	
	}
		
}  //end of node

def sendEmailNotificationDocker(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME, def imageName, def imageLabel) {
    echo "${currentBuild.currentResult}"
	echo "${emailDistribution}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.currentResult == "ABORTED"){
		echo "aborted"
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
		echo "${emailDistribution}"
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	} else{
		
		echo "Issue with System Results please check"
		
	}
}

///// The End
