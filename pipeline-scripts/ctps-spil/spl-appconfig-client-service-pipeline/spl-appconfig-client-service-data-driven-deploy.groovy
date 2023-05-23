//Lib and var omport setup
import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

@Field projectProperties

//Dummy target values

/*
Cat 1 envs
    'pre-prod': ['10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248'],
    'prod': ['10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207']
*/ 
targets = [
    'dev':  ['10.42.20.20','10.42.16.106','10.42.16.107','10.42.20.21'],
    'qa': ['10.42.82.92','10.42.82.93','10.42.82.239','10.42.82.240'],
    'uat':['10.42.50.93','10.42.50.94','10.42.50.200','10.42.50.201'],
    'load': ['10.42.5.203','10.42.5.204','10.42.5.205','10.42.5.206','10.42.5.207','10.42.5.208','10.42.5.209']

]

//Global Params
def gitCredentials = 'scm-incomm' 
gitRepository = "https://github.com/InComm-Software-Development/ctps-spil-shared-services-appconfigclient.git"
gitBranch = "origin/master" //going by convention, master should be the latest working code/config container.
allSnaps = []
imageLabel=''
emailDistribution = ''
imageName = ''
dockerRegistry = 'docker.maven.incomm.com'
approvalData = [
    'operators': "[ppiermarini,vpilli,vhari,mpalve,psubramanian]",
    'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve,psubramanian',
    'target_env' : "${targetenvs}"

]
Deploy_Config="${Config}"

//Initiated on the docker node
node('linux') {
    try {
    	//Git checkout  call
    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }
        
        stage('Approval Check') {
        	if (targetenvs == "uat" || targetenvs == "pre-prod" || targetenvs == "prod" || targetenvs == "pre-prod-cat1" || targetenvs == "prod-cat1") {
        		getApproval(approvalData)
        	}
        }
        
        //Reading the YML values
    	stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'dataDrivenDocker.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }
            echo "${projectProperties}"
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }
        }
        //Call to Docker Registry based on the imagename
        stage('Call to Docker Artifactory') {
        	if (projectProperties.imageInfo.imageName != null) {
        	allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
        	}
        }
        //API call to Docker Registry for userinput of the tag
        stage('Choose a tag') {
        	imageLabel = getImageTag(imageLabel, allSnaps)
            echo "${projectProperties.imageInfo.imageName}:${imageLabel}"
        }
        stage("Deploy to ${targetenvs}") {
   
            echo "{$targetenvs}"
            deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

        }

    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    sendEmailNotificationDocker(emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, targets, targetEnv,approvalData.get("target_env"), projectProperties.imageInfo.imageName,imageLabel)	
	}
} //end of node


//Deploy Docker Data Driven

def deployComponents(envName, targets, Deploy_Config){
    
    echo "my env= ${envName}"
    def stepsInParallel =  targets.collectEntries {
        [ "$it" : { deploy(it, envName, Deploy_Config) } ]
    }
    parallel stepsInParallel


    
}


def deploy(target_hostname, envName, Deploy_Config) {
    echo " the target is: ${target_hostname}"
//@TODO: Based of current command shell sequential pattern, possible modifications are nessary -- must be test!
    sshagent([credsName]) { 
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.configInfo.logLoc}'
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.configInfo.configLoc}'
        """

        if (Deploy_Config == 'Y'){
                    sh """
                        scp -q -o StrictHostKeyChecking=no ${projectProperties.gitInfo.gitLoc}/${projectProperties.gitInfo.gitYmlFile} root@${target_hostname}:${projectProperties.configInfo.configLoc}/
                    """
        }

        sh
        """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 ${projectProperties.configInfo.configLoc}/${projectProperties.gitInfo.gitYmlFile}'
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sed 's|.*image.*|    image: ${dockerRegistry}/${projectProperties.imageInfo.imageName}:${imageLabel}|g' ${projectProperties.configInfo.configLoc}/${projectProperties.gitInfo.gitYmlFile}'
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker-compose --file ${projectProperties.configInfo.configLoc}/${projectProperties.gitInfo.gitYmlFile} up -d'
        """
    }

}