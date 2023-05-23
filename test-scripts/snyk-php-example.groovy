import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _
credsName = "scm_deployment"
emailDistribution="rkale@incomm.com tkarnati@incomm.com thescox@InComm.com fharman@incomm.com vhari@incomm.com ppiermarini@incomm.com smathialagan@incomm.com ymilliyard@incomm.com" // 

mount="~/trnsx-uat-deploy"

snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
scanCodeDir="reports-code"

//@TODO: Ask 22-30 are possible to be data-driven
//trx_backend="trnx-backend.zip"
//trx_prod="trnx-prod.zip"

//tmp_path="/mnt/resource/blobfusetmp"
//config_file="/root/fuse_connection.cfg"
//tranx-backend.zip
//file_path_backend="/root/trnsx-uat-deploy"
//file_path_prod="/root/trnsx-uat-deploy"

jfrog = "/usr/bin/jfrog"

tranxArtRepo = "tranx/" 

gitRepository="${GIT_REPOSITORY}"
gitBranch="${Branch}"
gitCreds="scm-incomm"

now = new Date()
nowFormatted = now.format("MM-dd-YYYY HH:mm:ss", TimeZone.getTimeZone('UTC'))

backend_folder="trx_backend"
prod_folder="trx_prod"


node('linux2'){
	cleanWs()
	try {

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
            if (projectProperties.emailInfo.emailDistribution != null) {
                emailDistribution = projectProperties.emailInfo.emailDistribution
            }

            echo "Sanity Check"
            //@TODO: Check for null values per params
            if (projectProperties.blobfuseInfo.blobfuseMountLoc == null ||  projectProperties.blobfuseInfo.tmpLoc == null || projectProperties.blobfuseInfo.configFileLoc == null || 
             	projectProperties.sourceInfo.backendTranxLoc == null || projectProperties.sourceInfo.backendTranxFile == null || projectProperties.sourceInfo.prodTranxLoc == null || projectProperties.sourceInfo.prodTranxFile == null ||
             	projectProperties.ansibleInfo.playbook == null || projectProperties.ansibleInfo.ansibleLoc == null) {
                throw new Exception("Please add the key value pairs for the dataDrivenDocker yamls")
            }
        }

		stage('Checking Mount'){
		//echo "Checking the ${projectProperties.blobfuseInfoblobfuseMountLoc}"

		//echo "${projectProperties}"

		sshagent([credsName]) {
			check = sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'test -d ${projectProperties.blobfuseInfo.blobfuseMountLoc} && echo '1' || echo '0''""", returnStdout: true).trim()
				if ("${check}" == '1') {
				echo "The ${projectProperties.blobfuseInfo.blobfuseMountLoc} is mounted"
				}
				else {
				echo "The ${projectProperties.blobfuseInfo.blobfuseMountLoc} is not mounted"
				echo "proceeding to mount.."
				sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'blobfuse ${projectProperties.blobfuseInfo.blobfuseMountLoc} --tmp-path=${projectProperties.blobfuseInfo.tmpLoc} --config-file=${projectProperties.blobfuseInfo.configFileLoc} -o attr_timeout=240 -o entry_timeout=240 -o negative_timeout=120'""", returnStdout: true).trim()
				}	
			}
		}


		stage('Get Tranx Artifacts'){
		sshagent([credsName]) {
		
			dir("${backend_folder}") {
				sh """scp -o StrictHostKeyChecking=no root@10.114.144.132:${projectProperties.sourceInfo.backendTranxLoc}/${projectProperties.sourceInfo.backendTranxFile} ${WORKSPACE}"""

			}
			dir("${prod_folder}") {
				sh """scp -o StrictHostKeyChecking=no root@10.114.144.132:${projectProperties.sourceInfo.prodTranxLoc}/${projectProperties.sourceInfo.prodTranxFile} ${WORKSPACE}"""

			}
		}

		unzip zipFile: "${WORKSPACE}/${projectProperties.sourceInfo.backendTranxFile}", dir: "${backend_folder}"
		unzip zipFile: "${WORKSPACE}/${projectProperties.sourceInfo.prodTranxFile}", dir: "${prod_folder}"

	
		}
		
	 	
		stage('Snyk Auth & Test for Tranx') {
		
        	echo "initiate the syk auth for Tranx"
			withCredentials([string(credentialsId: 'snyk-tranx', variable: 'AUTH')]) {
				sh """${snyk} auth ${AUTH}
				cd ${WORKSPACE}/${backend_folder}/ && ${snyk} code test --json | ${snykHtml} -o Tranx-code-backend.html
				cd ${WORKSPACE}/${prod_folder}/ && ${snyk} code test --json | ${snykHtml} -o Tranx-code-prod.html
				${snyk} test ${WORKSPACE}/${backend_folder}/ --json --file=${WORKSPACE}/${backend_folder}/composer.lock | ${snykHtml} -o ${WORKSPACE}/${backend_folder}/Tranx-lock-backend.html
				${snyk} test ${WORKSPACE}/${prod_folder}/ --json --file=${WORKSPACE}/${prod_folder}/mycommercepointe/composer.lock | ${snykHtml} -o ${WORKSPACE}/${prod_folder}/Tranx-lock-prod.html
				"""
			}
        }
		
		stage('Publish Snyk Report') {
			dir("${scanCodeDir}") {
				sh """
				mv ${WORKSPACE}/${backend_folder}/Tranx-lock-backend.html ${WORKSPACE}/${scanCodeDir}
				mv ${WORKSPACE}/${backend_folder}/Tranx-code-backend.html ${WORKSPACE}/${scanCodeDir}
				mv ${WORKSPACE}/${prod_folder}/Tranx-lock-prod.html ${WORKSPACE}/${scanCodeDir}
				mv ${WORKSPACE}/${prod_folder}/Tranx-code-prod.html ${WORKSPACE}/${scanCodeDir}
				"""
			}

			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "Tranx-lock-backend.html",
	 			reportName: "Report for Tranx Backend 3rd Party",
	 			reportTitles: "The Report of Tranx Backend 3rd party Vuls"])

			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "Tranx-lock-prod.html",
	 			reportName: "Report for Tranx Prod 3rd Party",
	 			reportTitles: "The Report of Tranx Prod 3rd party Vuls"])

			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "Tranx-code-backend.html",
	 			reportName: "Report for Tranx Backend Code",
	 			reportTitles: "The Report of Tranx Code Code"])

			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "Tranx-code-prod.html",
	 			reportName: "Report for Tranx Prod Code",
	 			reportTitles: "The Report of Tranx Prod Code"])

		}
		

	}
 catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"
		
	} finally {
	echo "Sending Notification"

	}

} //end of node


