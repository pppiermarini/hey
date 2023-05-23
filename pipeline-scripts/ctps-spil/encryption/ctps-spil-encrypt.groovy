import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _
credsName = "scm_deployment"

gitRepository="${GIT_REPOSITORY}"
//Default push to origin/dev
gitBranch="jenkins"
gitCreds="scm-incomm"


emailDistribution="ppiermarini@incomm.com"
//General pipeline
targetenvs="${ENV}"
certPath="/app/k8s/spil-secrets"
//@TODO: Add the dev ip temporarily until Azure AD Vault piece is subscribed for. (Not sure what this means. -Paul)


target = [
    'dev':  ['10.42.50.200'],
    'qa':  ['10.42.50.200'],
    'uat':  ['10.42.50.200'],
    'pre-prod':  ['10.42.50.200'],
    'prod':  ['10.42.50.200'],
    'perf':  ['10.42.50.200']
]

//@TODO: Ask for the ctps namespace, possibly data driven? 
namespaceEnv = ""
clusterEnv = ""
gitcertDir = ""
gitsourceDir = ""

certnames=[]

//@Gary Collins will provide this if pub cert is updated. 
ctps_kubeseal_pub_key=""

node('linux2'){
	cleanWs() //keep for initial debugging
	
	try { 
		/*
		stage('Az Login') {
           //@TODO: Work with Mukund/CTPS to figure out az principle
            withCredentials([usernamePassword(credentialsId: 'ctps-sp', passwordVariable: 'PASS', usernameVariable: 'USER'),
            	string(credentialsId: 'ctps-tenant', variable: 'TENANT')]) {
            		sh"""
            		az login --service-principal --username ${USER} --tenant ${TENANT} --password ${PASS}
            		az keyvault certificate download --file ${filenames}
            		"""
			}
           
    	}*/

    	//@TODO: Remove after Az vault subscription completes for ctps

    	stage('Git Checkout') {
    		dir('checkout') {
    			githubCheckout(gitCreds,gitRepository,gitBranch)
    		}
 
    	}

    	stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'checkout/dataDrivenDocker.yml') //@TODO: Add dataDrivenDocker
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }

            namespaceEnv = projectProperties.k8s["${targetenvs}"].namespace
            clusterEnv = projectProperties.k8s["${targetenvs}"].cluster
            gitcertDir = projectProperties.gitInfo["${targetenvs}"].certsDir
            gitsourceDir = projectProperties.gitInfo["${targetenvs}"].sourceDir

			ctps_kubeseal_pub_key="/app/jenkins/cert-ctps/${clusterEnv}.crt"

           	echo "namespace: ${namespaceEnv}, cluster: ${clusterEnv}, gitcertdir: ${gitcertDir}, gitsourcedir: ${gitsourceDir}"
            echo "Sanity checks"
            if(namespaceEnv == null || clusterEnv == null || gitcertDir == null || gitsourceDir == null) {
                throw new Exception("Please check the dataDrivenDocker file for empty values or null assingments, the following is being passed to the pipeline: ${projectProperties}")
            }

        }


    	stage('Get Certs') {
    		deployComponents(targetenvs, target[targetenvs])

    	}

    	
    	stage('Encrypt certs') {
   			encryptCerts()
    	}

        
        stage("Pushing to ${gitBranch}") {
            sh """mv ${WORKSPACE}/encrypt-certs/* ${WORKSPACE}/checkout/shared-secrets/${gitcertDir}"""

            dir('checkout'){
				sh """
				cd ${WORKSPACE}/checkout/ && git pull origin ${gitBranch} && git add ${WORKSPACE}/checkout/shared-secrets/${gitcertDir}/* && git commit -m "adding/updating sealed secrets" && git push origin ${gitBranch}
				"""
            }
        }


    	/*
    	stage('Az Logout') {
           echo "Logging out securely"
           sh """
           az logout
      	   az cache purge
      	   az account clear
           """
    	}*/
    		cleanWs()// @TODO: Uncomment after successful run
	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    if (projectProperties.email.emailDistribution != null) {
    	sendCertNotification(projectProperties.email.emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, certnames)
    }
   
    echo "Pipeline has successfully encrypted the following certs: ${certnames}"

	}
		
}  //end of node

def deployComponents(envName, target){
	
	echo "my env= ${envName}"
	echo "my target= ${target}"
	def stepsInParallel =  target.collectEntries {
		[ "$it" : { deploy(it, envName) } ]
	}

	parallel stepsInParallel

	
}


def deploy(target_hostname, envName) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {
			dir('certs') {
				// getting certs from remote servers
				sh """scp -o StrictHostKeyChecking=no root@${target_hostname}:${certPath}/${gitsourceDir}/* ${WORKSPACE}/certs/"""

			}

		}
}


def encryptCerts() {
	echo "appending all certs to certnames list"
	def files = findFiles(glob: '**/certs/*') 
	files.each{cert -> 
		certnames.add("${cert.name}")
	}

	dir('encrypt-certs') {
		echo "Creating a dir for encrypted certs"
	}

	dir('certs') {
		certnames.each{ cert ->
    	certFilterName = cert.split("\\.")[0]
    	//@TODO get namespace from data driven file
    	//kubeseal --raw --from-file assurance_key.pem --scope cluster-wide --cert /app/jenkins/cert-ctps/kubeseal-public-dev.crt

    	echo "${namespaceEnv}"
    	value_encrypt = sh(script: """kubeseal --raw --from-file ${cert} --scope namespace-wide --namespace ${namespaceEnv} --cert ${ctps_kubeseal_pub_key}""", returnStdout: true).trim()
    
    	data = """${certFilterName}: ${value_encrypt}"""

    	writeFile file: "${WORKSPACE}/encrypt-certs/${certFilterName}", text: data
    	}
	}

}

def sendCertNotification(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME, def certnames) {
	echo "${emailDistribution}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
    emailext attachmentsPattern:  '**/*.html',
    mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
				    <li>Encrypted Certs: ${certnames}</li>
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
				    <li>Encrypted Certs: ${certnames}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
		echo "${emailDistribution}"
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
				    <li>Encrypted Certs: ${certnames}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	}
}

///// The End
