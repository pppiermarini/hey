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
credsName = 'scm_deployment'
/*
Cat 1 envs
    'pre-prod': ['10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248'],
    'prod': ['10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207']
    'prod':['10.40.7.231'],
    'pre-prod':['10.40.7.214']
*/ 
targetsMaster = [
    'dev':  ['10.42.20.20'],
    'qa': ['10.42.82.239'],
    'uat':['10.42.50.200'],
    'load':['10.42.5.203']

]


targetsAll = [
    'dev':  ['10.42.20.20','10.42.16.106','10.42.16.107','10.42.20.21']
]

//Global Params
def gitCredentials = 'scm-incomm' 
gitRepository = "https://github.com/InComm-Software-Development/ctps-spil-services-msftcompletewarranty.git"
gitBranch="${Branch}" //going by convention, master should be the latest working code/config container.
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


dockerRepo = "docker.maven.incomm.com"
Deploy_Config="${Config}"
Update_Cert= "${Cert}"

//Initiated on the docker node
node('linux1') {
    try {
    	//Git checkout  call

        stage('Approval Check') {
            if (targetenvs == "UAT" || targetenvs == "PRE-PROD" || targetenvs == "PROD" || targetenvs == "LOAD" || targetenvs == "pre-prod-cat1" || targetenvs == "prod-cat1") {
                getApproval(approvalData)
            }
        }

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }
        
        //Reading the YML values
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
            if (projectProperties.gitInfo.gitYmlFile == null || projectProperties.gitInfo.gitLoc == null ||
             projectProperties.configInfo.logLoc == null || projectProperties.configInfo.configLoc == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }

        stage("Cert Upload") {
            echo "Transferring cert to all {$targetenvs} targets"
            if (projectProperties.gitInfo.certFile != null && projectProperties.certInfo.certLoc != null) {
            uploadCerts(targetenvs, targetsAll[targetenvs])
            }
            else {
                echo "No cert file or location provided, skipping upload to {$targetenvs}"
            }
        }
        //Call to Docker Registry based on the imagename
        //@TODO: Ask CTPS, what should happen when Deploy_Config is N. Do we only want to see the stages of Call to artifactory and choose tag when config is yes?
        //
        stage('Call to Docker Artifactory') {
        	if (projectProperties.imageInfo.imageName != null && Deploy_Config == 'Y') {
        	allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
        	}
            else {
                echo "Only a cert update"
                //throw new Exception("Please add the Image Name")
            }
        }
        //API call to Docker Registry for userinput of the tag
        /*&& Deploy_Config == 'Y'
        else {
            echo "No Tag needed to pick, this is service Restart"
        }
        */
        stage('Choose a tag') {
        if (projectProperties.imageInfo.imageName != null && Deploy_Config == 'Y') {
        	imageLabel = getImageTag(imageLabel, allSnaps)
            echo "${projectProperties.imageInfo.imageName}:${imageLabel}"
        }
        else {
           echo "Only a cert update"
        }

        }

        stage("Cert Update") {
            echo "{$targetenvs}"
            echo "deploy cert set to : " + Update_Cert

        if (Update_Cert == 'Y' && projectProperties.gitInfo.certFile != null) {
            echo "Master box for ${targetenvs} is ${targetsMaster[targetenvs]}"
            updateCert(targetenvs, targetsMaster[targetenvs], Update_Cert)
            }
        else {
                echo "Not a cert update"
            }
        }

        stage("Deploy to ${targetenvs}") {
        if (projectProperties.imageInfo.imageName != null && Deploy_Config == 'Y') {
            echo "{$targetenvs}"
            deployComponents(targetenvs, targetsMaster[targetenvs], Deploy_Config)
        }
        else {
            echo "Only a cert update"
        }

        }

    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    sendEmailNotificationDocker(emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, targetsMaster, targetenvs,approvalData.get("target_env"), projectProperties.imageInfo.imageName,imageLabel)	
	}
} //end of node


def uploadCerts(envName, targets){
    
    echo "my env= ${envName}"
    def stepsInParallel =  targets.collectEntries {
        [ "$it" : { deployCerts(it, envName) } ]
    }
    parallel stepsInParallel   
}


def deployCerts(target_hostname, envName) {
    echo "the target is: ${target_hostname}"
    echo "Env is: ${envName}"
    echo "Creating log + tranferring certs"
    sshagent([credsName]) {
        echo "Environment: ${envName}"
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.certInfo.certLoc}'
        scp -q -o StrictHostKeyChecking=no ${projectProperties.gitInfo.gitLoc}/${envName}/${projectProperties.gitInfo.certFile} root@${target_hostname}:${projectProperties.certInfo.certLoc}/"""
    }

}


def updateCert(envName, targets, Update_Cert){
    
    echo "my env= ${envName}"
    deployUpdate(targetsMaster.get(envName)[0], envName, Update_Cert)
}


def deployUpdate(target_hostname, envName, Update_Cert) {
    echo " the target is: ${target_hostname}"
    echo "Env is: ${envName}"
    envName = envName.toUpperCase()
    echo "UpperCase: ${envName}"
    sshagent([credsName]) {
        echo "deploy cert set to : " + Update_Cert
        echo "Environment: ${envName}"
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} docker service update ${envName}_${projectProperties.imageInfo.serviceName}''"""
    }

}


//Deploy Docker Data Driven

def deployComponents(envName, targets, Deploy_Config){
    
    echo "my env= ${envName}" 
    deploy(targetsMaster.get(envName)[0], envName, Deploy_Config)

}


def deploy(target_hostname, envName, Deploy_Config) {
    echo " the target is: ${target_hostname}"
    echo "Env is: ${envName}"
    sshagent([credsName]) { 
        echo "Creating config directory if doesn't exit"
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.configInfo.logLoc}'
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.configInfo.configLoc}'
        """
        echo "deployconfig set to : " + Deploy_Config
        //@TODO: When should the sed subsitution get invoked -- confirm it's part of config deploy
            if (Deploy_Config == 'Y'){
                    sh """
                        scp -q -o StrictHostKeyChecking=no ${projectProperties.gitInfo.gitLoc}/${envName}/${projectProperties.gitInfo.gitYmlFile} root@${target_hostname}:${projectProperties.configInfo.configLoc}/
                        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sed -i "s|.*image.*|    image: ${dockerRepo}/${projectProperties.imageInfo.imageName}:${imageLabel}|g" ${projectProperties.configInfo.configLoc}/${projectProperties.gitInfo.gitYmlFile}'
                    """
            }

        envName = envName.toUpperCase()
        echo "UpperCase: ${envName}"
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${projectProperties.configInfo.configLoc}'
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker stack deploy --compose-file ${projectProperties.configInfo.configLoc}/${projectProperties.gitInfo.gitYmlFile} ${envName} --with-registry-auth'
        """
    }

}