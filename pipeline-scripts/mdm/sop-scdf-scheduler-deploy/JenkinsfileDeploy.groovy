import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//sopownership@incomm.com
emailDistribution="rkale@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

targetenvs="${targetEnv}"


configLoc="/var/opt/sop-scdf-scheduler/"

configLogLoc="/var/opt/sop-scdf-scheduler/logs/"

imageName = "sop-scdf-scheduler"

gitRepository="https://github.com/InComm-Software-Development/sop-fusion-configuration.git"
gitBranch="origin/master"
gitCreds="scm-incomm"

configListLoc="/var/opt/sop-scdf-scheduler/sop-scdf-scheduler-env.list"

chmod="400"


//Refer to: https://wiki.incomm.com/pages/viewpage.action?pageId=141008525
//'10.42.20.20','10.42.16.106','10.42.16.107'
targets = [
    'dev':  ['10.42.17.64','10.42.17.67']
]
userInput="Promote"

allSnaps = []


node('docker'){
	try { 
		
		cleanWs()

		stage('Git Checkout'){
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}

		//Call to Docker Registry based on the imagename
        stage('Call to Docker Artifactory') {
        	allSnaps = callDockerRegistry(imageName)
        }
        //API call to Docker Registry for userinput of the tag
        stage('Choose a tag') {
        	imageLabel = getImageTag(imageLabel, allSnaps)
        }


		stage("Deploy to ${targetenvs}") {
			echo "{$targetenvs}"
			deployComponents(targetenvs, targets[targetenvs], imageLabel, imageName)

		}

		
	} catch(exc) {
			currentBuild.result="FAILED"
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
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.result == "ABORTED"){
		echo "if aborted"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
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
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.result == "SUCCESS"){
		echo "success"
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


def deployComponents(envName, targets, imageLabel, imageName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, imageLabel, imageName) } ]
	}
	parallel stepsInParallel


	
}

def deploy(target_hostname, envName, imageLabel, imageName) {
	echo " the target is: ${target_hostname}"
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker import ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	// 

	/*
			echo "Generation of log directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLogLoc}'
		"""
		echo "Copying the config list files based on Env"
		sh """
		scp -q -o StrictHostKeyChecking=no -r ${imageName}/${envName}/* root@${target_hostname}:${configLoc}"""

		echo "Setting permissions on the files"

	    sh"""ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R nobody:nobody ${configLoc}'
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown root:root ${configListLoc}'
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${configListLoc}'
		"""
	*/

	sshagent([credsName]) {	



		echo "Docker stop and run"
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker stop ${imageName} && docker rm ${imageName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker run -d --name ${imageName} --restart=always --net host --user 99:99 --env-file ${configListLoc} -v ${configLoc}:${configLoc} docker.maven.incomm.com/${imageName}:${imageLabel}'
		"""
	}

}



///// The End
