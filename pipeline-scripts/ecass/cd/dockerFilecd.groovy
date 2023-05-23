import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _
credsName = "scm_deployment"
emailDistribution="rkale@incomm.com"

now = new Date()
tStamp = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
archiveFolder="archive_${tStamp}"

gitCreds="scm-incomm"
registry_acr="muse2lleb2bcr001.azurecr.io"
registry_art="docker.maven.incomm.com"


approvalData = [
    'operators': "[apillai]",
    'adUserOrGroup' : 'apillai',
    'target_env' : "${registry_acr}"
]
allSnaps=""
imageLabel=""
gitRepository = "${GIT_REPOSITORY}"
gitBranch="${Branch}"
node('linux2'){
	cleanWs()
	try { 

		stage('Approval Check') {
           
            getApproval(approvalData)
           
        }

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

        stage('Call to Docker Artifactory') {
        	if (projectProperties.imageInfo.imageName != null) {
        	allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
        	}
            else {
            throw new Exception("Please add the image name")

            }
        }
        //API call to Docker Registry for userinput of the tag
        stage('Choose a tag') {
        if (projectProperties.imageInfo.imageName != null) {
        	imageLabel = getImageTag(imageLabel, allSnaps)
            echo "Image to be pulled ${projectProperties.imageInfo.imageName}:${imageLabel}"
        }
        else {
        throw new Exception("Please add the image name")

        }

        }


        stage('Pull from Artifactory') {
        if (projectProperties.imageInfo.imageName != null && imageLabel != null) {
        	dockerPull("${registry_art}", "${projectProperties.imageInfo.imageName}","${imageLabel}")
        }
        else {
        throw new Exception("Something went wrong please check")

        }

        }

        stage('Az Login and Push to ACR') {
           echo "Logging into ${registry_acr}"
           //@TODO: Work with Aravind P. to figure out the az import command into ecass registry
            withCredentials([usernamePassword(credentialsId: 'ecass-acr-sp', passwordVariable: 'PASS', usernameVariable: 'USER'),
            string(credentialsId: 'ecass-acr-tenant', variable: 'TENANT')]) {
            	sh"""
            	az login --service-principal --username ${USER} --tenant ${TENANT} --password ${PASS}
            	az acr login --name ${registry_acr}
            	docker tag ${registry_art}/${projectProperties.imageInfo.imageName}:${imageLabel} ${registry_acr}/${projectProperties.imageInfo.imageName}:${imageLabel}
            	docker push ${registry_acr}/${projectProperties.imageInfo.imageName}:${imageLabel}
            	"""
			}
           
        }

        stage('Az Logout') {
           echo "Logging out securely"
           sh """
           az logout
      	   az cache purge
      	   az account clear
           """
        }

        stage('Cleanup Docker Image') {
			
            sh '''docker images -a | grep ''' + projectProperties.imageInfo.imageName + '''| awk \'{print $3}\' | xargs docker rmi -f'''

        }



	}

 catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node

///// The End