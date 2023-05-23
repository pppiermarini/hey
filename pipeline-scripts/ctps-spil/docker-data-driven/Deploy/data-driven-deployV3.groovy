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

targetsMaster = []
targetsAll = []


//@TODO: Confirm the following array with CTPS Software Solution Team

/*
Spil Target Boxes - Master and All Nodes
*/

/*
    'cat-1-dev':  ['10.42.16.16'],
    'cat-1-qa':   ['10.42.82.250'],
    'cat-1-uat':  ['10.42.49.100'],
*/
targetsMasterSpil = [
    'dev':  ['10.42.20.20'],
    'qa':   ['10.42.82.239'],
    'uat':  ['10.42.50.200'],
    'load': ['10.42.5.203'],
    'pre-prod': ['10.40.7.214'],
    'prod': ['10.40.7.231']
]
/*
    'cat-1-dev':  ['10.42.16.16','10.42.16.18','10.42.16.21','10.42.16.125'],
    'cat-1-qa':   ['10.42.82.250','10.42.82.251','10.42.82.252','10.42.82.253'],
    'cat-1-uat':  ['10.42.49.100','10.42.49.101','10.42.49.182','10.42.49.184'],
*/
targetsAllSpil = [
    'dev':  ['10.42.20.20','10.42.16.107','10.42.20.21'],
    'qa':   ['10.42.82.239','10.42.82.240','10.42.82.92','10.42.82.93'],
    'uat':  ['10.42.50.200','10.42.50.201','10.42.50.93','10.42.50.94'],
    'load': ['10.42.5.203','10.42.5.204','10.42.5.205','10.42.5.206','10.42.5.207','10.42.5.208','10.42.5.209'],
    'pre-prod': ['10.40.7.214','10.40.7.215','10.40.7.229','10.40.7.230'],
    'prod': ['10.40.7.231','10.40.7.232','10.40.7.233','10.40.7.210','10.40.7.211','10.40.7.212','10.40.7.213']
]

/*
Mil Target Boxes - Master and All Nodes
*/
targetsMasterMil = [
    'dev':  ['10.42.16.16'],
    'qa': ['10.42.82.250'],
    'uat': ['10.42.49.100'],
    'pre-prod': ['10.40.98.222'],
    'prod': ['10.40.98.230']
]

targetsAllMil = [
    'dev':  ['10.42.16.16','10.42.16.18','10.42.16.21','10.42.16.125'],
    'qa': ['10.42.82.250','10.42.82.251','10.42.82.252','10.42.82.253'],
    'uat': ['10.42.49.100','10.42.49.101','10.42.49.182','10.42.49.184'],
    'pre-prod': ['10.40.98.222','10.40.98.223','10.40.98.228','10.40.98.229'],
    'prod': ['10.40.98.230','10.40.98.231','10.40.98.232','10.40.98.233']
]

targetsMasterSpilSrl = [
    'dev':  ['10.42.16.16'],
    'qa': ['10.42.82.250'],
    'uat': ['10.42.49.100']
]

targetsAllSpilSrl = [
    'dev':  ['10.42.16.16','10.42.16.18','10.42.16.21','10.42.16.125'],
    'qa': ['10.42.82.250','10.42.82.251','10.42.82.252','10.42.82.253'],
    'uat': ['10.42.49.100','10.42.49.101','10.42.49.182','10.42.49.184']
]



/*
Secure Spil Target Boxes - Master and All Nodes
*/
targetsMasterSecureSpil = [
'pre-prod': ['10.41.4.218'],
'prod': ['10.41.6.97']
]


targetsAllSecureSpil = [
'pre-prod': ['10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248'],
'prod': ['10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207']
]


//Global Params
def gitCredentials = 'scm-incomm' 
gitRepository = "${GIT_REPOSITORY}"
gitBranch="${Branch}" //going by convention, master should be the latest working code/config container.
allSnaps = []
imageLabel=''
emailDistribution = ''
imageName = ''
dockerRegistry = 'docker.maven.incomm.com'
approvalData = [
    'operators': "[ppiermarini,vpilli,vhari,mpalve,psubramanian,fharman,nkassetty,damiller]",
    'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve,psubramanian,fharman,nkassetty,damiller',
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
            if (targetenvs == "uat" || targetenvs == "pre-prod" || targetenvs == "prod" || targetenvs == "load" || targetenvs == "pre-prod-cat1" || targetenvs == "prod-cat1") {
                getApproval(approvalData)
            }
        }
    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }

        stage('Define Targets') {
            defineTargets()
            echo "Target Env: ${targetenvs}: Master Node:"
            println  targetsMaster[targetenvs]
            echo "Target Env: ${targetenvs}: All Targets:"
            println  targetsAll[targetenvs]
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

        stage('Cert Checkouts'){
        if (projectProperties.certInfo.certURL != null && projectProperties.certInfo.certBranch != null) {
            dir('certRepo'){
                githubCheckout(gitCredentials, projectProperties.certInfo.certURL, projectProperties.certInfo.certBranch)
            }
        }
        else {
            echo "Skipping checkout.. no certInfo attributes found"
        }

        }

        stage("Cert Upload") {
            echo "Transferring cert to all {$targetenvs} targets"
            if (projectProperties.certInfo.certFiles != null && projectProperties.certInfo.certLoc != null) {

                certs = projectProperties.certInfo.certFiles.trim()
                certsAll = "${certs}".split(",")
                echo "${certsAll}"
                
                for (String cert : certsAll) {
                    filterCertTrim = cert.trim()
                    uploadCerts(targetenvs, targetsAll[targetenvs], filterCertTrim)

                }
            }
            else {
                echo "No cert file(s) or location provided, skipping upload to ${targetenvs}"
            }
        }
        //Call to Docker Registry based on the imagename
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

        if (Update_Cert == 'Y' && projectProperties.certInfo.certFiles != null) {
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


def uploadCerts(envName, targets, currentCert){
    
    echo "my env= ${envName}"
    def stepsInParallel =  targets.collectEntries {
        [ "$it" : { deployCerts(it, envName, currentCert) } ]
    }
    parallel stepsInParallel   
}


def deployCerts(target_hostname, envName, currentCert) {
    echo "the target is: ${target_hostname}"
    echo "Env is: ${envName}"
    echo "Creating log + tranferring certs"
    sshagent([credsName]) {
        echo "Environment: ${envName}"
        sh """
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${projectProperties.certInfo.certLoc}'"""
        if (targetenvs == "pre-prod" || targetenvs == "prod") {
            echo "Copying Prod cert(s)"
            sh """
            scp -q -o StrictHostKeyChecking=no certRepo/prod/${currentCert} root@${target_hostname}:${projectProperties.certInfo.certLoc}/
            """
        }
        else {
            echo "Copying LLE cert(s)"
            sh """
            scp -q -o StrictHostKeyChecking=no certRepo/lle/${currentCert} root@${target_hostname}:${projectProperties.certInfo.certLoc}/
            """
        }
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
        ssh -q -o StrictHostKeyChecking=no root@${target_hostname} docker service update --force ${envName}_${projectProperties.imageInfo.serviceName}''"""
    }
// docker service update updates the certs in the container
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
    }  // docker stack is a cluster management command, and must be executed on a swarm manager node

}

def defineTargets() {
    println("${JOB_NAME}")
    
    println("${JOB_NAME}".split("/")[0].toLowerCase())

    if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /secure-spil-svc-pipelines/) {
        targetsMaster = targetsMasterSecureSpil
        targetsAll = targetsAllSecureSpil
    }

    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /spil-svc-pipelines/) {
        targetsMaster = targetsMasterSpil
        targetsAll = targetsAllSpil
    }
    
    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /spilsrl-svc-pipelines/) {
        targetsMaster = targetsMasterSpilSrl
        targetsAll = targetsAllSpilSrl
    }
    else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /mil-svc-pipelines/) {
        targetsMaster = targetsMasterMil
        targetsAll = targetsAllMil
    }
    else {
        throw new Exception("No Application Targets found for the defined application")

    }

}