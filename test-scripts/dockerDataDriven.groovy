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
targets = [
    'dev':  ['v1','v2'],
    'qa': ['v3','v4']
    ]

//Global Params
def gitCredentials = 'scm-incomm' 
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${BRANCH}"
allSnaps = []
imageLabel=''
emailDistribution = ''
imageName = ''
dockerRepo = 'docker.maven.incomm.com'
approvalData = [
	'operators': "[vhari,ppiermarini,rkale]",
	'adUserOrGroup' : 'vhari,ppiermarini',
	'target_env' : "${targetEnv}"

]
registry="docker.maven.incomm.com"
//Initiated on the docker node
node('docker') {
    try {
    	//Git checkout  call
    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }
        
        stage('Approval Check') {
        	if (targetEnv == 'qa') {
        		getApproval(approvalData)
        	}
        }
        
        //Reading the YML values
    	stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'test-yamls/dataDrivenDocker.yml')
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
        //Docker Pull call to save the docker image as a tarball on Jenkins workspace. 
        
        stage('Docker Pull') {
        	dockerPull(registry ,imageLabel, projectProperties.imageInfo.imageName) 
           /* sh """
            docker pull ${registry}/${projectProperties.imageInfo.imageName}:${imageLabel}
            docker save -o ${projectProperties.imageInfo.imageName}-${imageLabel}.tar docker.maven.incomm.com/${projectProperties.imageInfo.imageName}:${imageLabel}
            """ */

            sh """
            ls -ltra
            """
        }
        
        stage('Cleanup') {
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi'''

        }

    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    } finally {
    //Sending a bunch of information via emali to the email distro list of participants	
    sendEmailNotificationDocker(emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, targets, targetEnv,approvalData.get("target_env"), projectProperties.imageInfo.imageName,imageLabel)	
	}
} //end of node
