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

//Global Params
def gitCredentials = 'scm-incomm' 
gitRepository = "https://github.com/InComm-CoreTPS/ctps-spil-services-secret-helm-charts.git"
gitBranch = "origin/dev"
allSnaps = []
imageLabel=''
emailDistribution = ''
imageName = ''

targetenv = "${ENV}"
namespaceEnv = ""
clusterEnv = ""
gitcertDir = ""
gitsourceDir = ""

node('linux2') {
    try {
    	//Git checkout  call
        cleanWs()
    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }
        
        //Reading the YML values
    	stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'test-yamls/dataDrivenCert.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }

            namespaceEnv = projectProperties.k8s["${targetenv}"].namespace
            clusterEnv = projectProperties.k8s["${targetenv}"].cluster
            gitcertDir = projectProperties.gitInfo["${targetenv}"].certsDir
            gitsourceDir = projectProperties.gitInfo["${targetenv}"].sourceDir

            echo "${namespaceEnv}, ${clusterEnv}, ${gitcertDir}, ${gitsourceDir}"
            echo "Sanity checks"
            if(namespaceEnv == null || clusterEnv == null || gitcertDir == null || gitsourceDir == null) {
                throw new Exception("Please check the dataDrivenDocker file for empty values or null assingments, the following is being passed to the pipeline:  ${projectProperties}")
            }

            

        }


    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    } finally {
    //Sending a bunch of information via emali to the email distro list of participants	
    // sendEmailNotificationDocker(emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, targets, targetEnv,approvalData.get("target_env"), projectProperties.imageInfo.imageName,imageLabel)	
	}
} //end of node
