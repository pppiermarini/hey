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
vulnerabilityFound=""
imageLabel=""
labelScan = "${env.BUILD_ID}"
maven="/opt/apache-maven-3.8.4/bin/mvn"
jfrog = "/usr/bin/jfrog"
jdkVersion=""

scanCodeDir="reports-code"
scanContainerDir="reports-container"
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
            if (projectProperties.imageInfo.imageName == null) {
                throw new Exception("Please add the image name")
            }
        }
        
        stage('Setting JDK') {
			jdk=tool name:"openjdk-11.0.7.10-0"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}
		/*
		stage('Snyk Auth for Ecass') {
        	
        	echo "Snyk auth for Ecass"
        	/*
        	withCredentials([string(credentialsId: 'snyk-ctps', variable: 'AUTH')]) {
				sh "${snyk} auth ${AUTH}"
			}
        }*/
		/*
		stage('Scan'){

				//@TODO: Test snyk container dockerfile test
				//sh"${snyk} test --json --severity-threshold=low | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${labelScan}-code.html"

				//sh"${snyk} container test ${projectProperties.imageInfo.imageName} --file=Dockerfile --json | ${snykHtml} -o ${projectProperties.imageInfo.imageName}-${labelScan}-container.html"
			
		}//stage
		
		
		stage('Report') {
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
			}
				
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
		
		
		stage('Build-Release'){

			if (userInput == 'Build') {
				if (projectProperties.imageInfo.imageName != 'null') {
					//@TODO: How are we doing versioning/tagging of container? 
					
						withCredentials([usernamePassword(credentialsId: 'ecass-b2bconfigserver-unit-test', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
						sh """${maven} package -Djasypt.encryptor.password="${PASS}" -Dspring.profiles.active=native"""


				}
					//, "--build-arg user=jenkins ."
					def dockerImage = docker.build("${projectProperties.imageInfo.imageName}:${labelScan}", "-f Dockerfile --no-cache .") // @TODO: need to dynamically input image paramters and version, datadriven, pom file or ${env.BUILD_ID}
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
					sh """ ${jfrog} rt docker-promote ${projectProperties.imageInfo.imageName} docker-local docker-prod --source-tag="${imageLabel}" --target-tag="${imageLabel}-RELEASE" --copy=true"""
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
