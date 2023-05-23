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


//globals

currentBuild.result = "SUCCESS"
emailDistribution="rkale@incomm.com "

targetenvs="${targetEnv}"

//red-db-services,spl-appconfig-client-service
imageName = "spl-appconfig-client-service"


now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

targets = [
    'dev':  ['v1','v2'],
    'qa': ['v3','v4'],
    'pre-prod': ['v5','v6'],
    'prod': ['v7','v8']
]


def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''
didTimeout = false

node('docker'){ 
	try { 
		cleanWs()

		stage('Approval'){
			wrap([$class: 'BuildUser']) {	
			if((userInput== 'Promote' && targetenvs == 'pre-prod') || (userInput== 'Promote' && targetenvs == 'prod')) {	
                    echo "Inside approval block"
                    def user_deploying = "${BUILD_USER_ID}"
                    operators= "['rkale']"
                    try {
                	timeout(time: 90, unit: 'SECONDS'){
                    	Deployment_approval = input message: "Deploying to ${targetEnv}", ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
                    	}
                    }
                    catch (err) {
                    	echo "Build Aborted"
						currentBuild.result = "ABORTED"
                    }
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operator = "${Deployment_approval['approver']}"
					//String op = operator.toString()

                    if (approval_status == 'Proceed'){
                        echo "Operator is ${operator}"
                        if (operators.contains(user_deploying))
      		            {
                            echo "${operator} is allowed to deploy into ${targetEnv}"
							echo "Proceeding to get Artifact"
		                }
		                else
		                {
		                	currentBuild.result = "FAILURE"

		                    throw new Exception("Throw to stop pipeline as user not in approval list")
		                }
                    }else {
                    	 	currentBuild.result = "ABORTED"

                    throw new Exception("Throw to stop pipeline as user selected abort")
                    }
			}
			else {
				echo "${targetEnv} enviornment selected"
				echo "Proceeding to get Artifact"

			}
			} 
        	
		}

	stage('Call to Docker'){

			final String call = "https://docker.maven.incomm.com/artifactory/api/docker/docker-local/v2/${imageName}/tags/list"
	
	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {

			response = sh(script: "curl -X GET ${call} -u ${USER}:${PASS}", returnStdout: true).trim()
			def dataApi = readJSON text: response
			
			for (i in dataApi.tags) {
				allSnaps.add(i)
			}
		}
	}


	stage('Choose a tag') {
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

	}
}
		 catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	echo "${currentBuild.result}"
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	//<li>ServerNames: ${targetEnv}= ${Servers}</li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}
	else if (currentBuild.result == "ABORTED"){
		echo "if aborted"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	//<li>ServerNames: ${targetEnv}= ${Servers}</li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}
	else if(currentBuild.result == "SUCCESS"){
		Servers = targets.get(targetEnv)
		echo "if success"
    	emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""		
	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node

///// The End