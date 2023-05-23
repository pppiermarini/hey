import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


//"shaque@incomm.com rkale@incomm.com vhari@incomm.com skeshari@InComm.com mmurarisetty@incomm.com stadoori@incomm.com"
emailDistribution="rkale@incomm.com vhari@incomm.com stadoori@incomm.com mmurarisetty@incomm.com skeshari@InComm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

//Image Name at Maven Docker registry
imageName = "appconfig-client-service"

imageDeploymentLoc="/app/docker/tmp"

Deploy_Config="${Config}"

//Config logs
configLogs="/app/install/appconfigclient/logs"

//@TODO: Verify Config Loc
configLoc="/app/install/appconfigclient/"

//@TODO: Need to know where to scp the prop file
configAppLoc="/app/config/"

deploy_config_application="epp-con"

gitLoc="src/main/resources/docker/"

gitAppLoc="src/main/resources/"


gitYmlInstallFile="acf-web-client-docker-compose.yml"

commandFile="command.sh"

appFile="AppConfigCmd.sh"

targetenvs="${targetEnv}"

gitRepository="https://github.com/InComm-Software-Development/ctps-rtg-shared-services-appconfigclient.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


//Updated Targets
//@Vamsi please validate prod-azure
targets = [
    'pre-prod':['10.41.7.130','10.41.7.131'],
    'prod':['10.41.7.126','10.41.7.127','10.41.7.128'],
    'prod-azure': ['10.114.96.166','10.114.96.165','10.114.96.164']
]


//Approval Data for deployment
approvalData = [
	'operators': "[ppiermarini,skeshari,vhari,stadoori,wzhan]",
	'adUserOrGroup' : 'ppiermarini,skeshari,vhari,stadoori,wzhan',
	'target_env' : "${targetEnv}"
]


def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''

node('linux'){
	try { 
			
		cleanWs()
		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod') || (targetEnv == 'qa') || (targetEnv == 'uat') || (targetEnv == 'prod-azure')) {
        		getApproval(approvalData)
        	}
        }

		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)
			deployComponents(targetenvs, targets[targetenvs], Deploy_Config, deploy_config_application)

		}
	
		
	} catch(exc) {
			//currentBuild.currentResult ="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	echo "${currentBuild.currentResult}"
	if (currentBuild.currentResult == "FAILURE"){
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
					<li>STATUS: ${currentBuild.currentResult}</li>
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
	else if (currentBuild.currentResult == "ABORTED"){
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
					<li>STATUS: ${currentBuild.currentResult}</li>
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
	else if(currentBuild.currentResult == "SUCCESS"){
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
					<li>STATUS: ${currentBuild.currentResult}</li>
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


def deployComponents(envName, targets, Deploy_Config, deploy_config_application){

	echo "my env= ${envName}"
	echo "my target= ${targets}"
	echo "Deploy config: " + Deploy_Config
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, Deploy_Config, deploy_config_application) } ]
		}
	

	parallel stepsInParallel

	
}




def deploy(target_hostname, envName, Deploy_Config, deploy_config_application) {
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		echo "Deploying the image"
		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLogs}'
		"""
		echo "deployconfig set to : " + Deploy_Config

		/*
		Refactoring for later:
		scp -q -o StrictHostKeyChecking=no ${gitLocLogLoc}/${logLoc} root@${target_hostname}:${configLogEncrypt}
		scp -q -o StrictHostKeyChecking=no ${gitLocLogLoc}/${encrypt} root@${target_hostname}:${configLogEncrypt}
		*/
		if (Deploy_Config == 'Y'){
			sh """
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}
				scp -q -o StrictHostKeyChecking=no ${gitAppLoc}/${deploy_config_application}/${envName}/* root@${target_hostname}:${configAppLoc}
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${appFile} root@${target_hostname}:${configLoc}
			    ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configAppLoc}'
			   	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${appFile}'

			"""
		}

		/*
		Refactoring for later:
 		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLogEncrypt}'
		*/
		else  {
	    sh"""
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${commandFile} root@${target_hostname}:${configLoc}
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${commandFile}'

		"""
		}

		}
}



///// The End
