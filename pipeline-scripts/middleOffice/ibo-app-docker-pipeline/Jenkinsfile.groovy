import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import java.text.SimpleDateFormat
import java.lang.*
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk
//
// 

// Docker Code Repo
// '/' at the end of this url is required. do not remove that
developmentBranch_Docker="https://svn.incomm.com/svn/devel/tp/inVision/docker-ibo-pipeline/branches/"
releaseTrunk_Docker="https://svn.incomm.com/svn/devel/tp/inVision/docker-ibo-pipeline/trunk/docker-ibo-app-pipeline/"

// Jenkin path for ibo.tgz
//iboBuildFilePath_1 = "https://build.incomm.com/view/IBO/job/ibo-ict_"
//iboBuildFilePath_2 = "/lastSuccessfulBuild/artifact/tar/ibo.tgz"
iboBuildFilePath_1 = "https://build.incomm.com/view/MiddleOffice_Pipelines/job/ibo-app-docker-"
iboBuildFilePath_2 = "/lastSuccessfulBuild/artifact/tar/ibo.tgz"

//emailDistribution="middleofficeownership@incomm.com"
emailDistribution="tmalik@incomm.com"
//General pipeline 
srcArtifactLocation="${env.WORKSPACE}"
pipelineData="/app/pipeline-data/ibo-app-docker-pipeline"
dockerBuildScriptDir="build-docker-image"
dockerDeployScriptDir="deploy-docker-image"
rpmExtractDir = "rpm_extract"
pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

//globals
userApprove="null"
userInput="null"
envInput="null"
target_env=""

//for deployment approvals
def approver = ''
def approval_status = '' 
def operators = []
approvalData = [
	'operators': "[ppiermarini,,vhari,rkale,glindsey,jwhiteley,smathialagan]",
	'adUserOrGroup' : 'ppiermarini,,vhari,rkale,glindsey,jwhiteley,smathialagan',
	'target_env' : "${target_env}"
]

localArtifact="null"
svnrevision="null"
relVersion="null"
//userInput = InputAction()
echo "Lib User Input = $userInput"
codeBuildJobParam="trunk"
branchVersion="null"
finalSVNPath_Docker = "trunk"
dockerBranchVersion="8.0.0"

//variables
buildType = "null"
buildPath = ""
userInputCodeSource = "trunk"
userInputDockerScriptSource = ""
devBuildJenkinsPath = ""
prodReadyBuildJenkinsPath = ""
envlevel="null"
instanceInput="null"
instanceInput_fdt_all_lle=["fdt01", "fdt02"]
instanceInput_ict_all_lle=["ict01", "ict02"]
instanceToDeploy="null"
hostMap = [:]
hostMap.dev_host_fdt01 = "sdiboapp01v.unx.incommtech.net"
//hostMap.dev_host_fdt01 = "spliboappfdt01bv.unx.incommtech.net" // TODO Only for testing purpose. DO NOT uncomment if you don't need this.
hostMap.dev_host_fdt02 = "sdiboapp02v.unx.incommtech.net"
hostMap.dev_prop_fdt01 = "sdiboapp01v.unx.incommtech.net"
dev_all_host_fdt = ["sdiboapp01v.unx.incommtech.net", "sdiboapp02v.unx.incommtech.net"]
hostMap.dev_host_ict01 = "sdiboappict01v.unx.incommtech.net"
dev_all_host_ict = ["sdiboappict01v.unx.incommtech.net"]
hostMap.qa_host_fdt01 = "sqiboapp01v.unx.incommtech.net"
hostMap.qa_host_fdt02 = "sqiboapp02v.unx.incommtech.net"
qa_all_host_fdt = ["sqiboapp01v.unx.incommtech.net", "sqiboapp02v.unx.incommtech.net"]
hostMap.qa_host_ict01 = "sqiboappict01v.unx.incommtech.net"
qa_all_host_ict = ["sqiboappict01v.unx.incommtech.net"]
hostMap.uat_host_fdt01 = "suliboapp03av.unx.incommtech.net"
hostMap.uat_host_fdt02 = "suliboapp04av.unx.incommtech.net"
uat_all_host_fdt = ["suliboapp03av.unx.incommtech.net", "suliboapp04av.unx.incommtech.net"]
hostMap.uat_host_ict01 = ""
uat_all_host_ict = [""]
instance_Prod=["qtsfdt01", "qtsfdt02", "qtsict01", "qtsict02", "qtsbat01", "qtsbat02"]
prod_host_all=["spiboappfdt01v.unx.incommtech.net", "spiboappfdt02v.unx.incommtech.net", "spiboappict01v.unx.incommtech.net", "spiboappict02v.unx.incommtech.net", "spiboappbat01v.unx.incommtech.net", "spiboappbat02v.unx.incommtech.net"]
envInput_lowerCase = ""
IMAGE_ID=""
imageTagVersion=""

node('docker') {

	try {
		cleanWs()

		stage('input') {
			try {
				timeout(time: 30, unit: 'SECONDS') {
					userInput = input message: 'User input required', ok: 'Continue',
						parameters: [choice(name: 'Code Build or Docker Build or Promote?', choices: 'Build\nDocker Build\nPromote', defaultValue: Build, description: 'Build or Promote?')]
				}
				echo "${env.userInput}"
				echo "selection"
			} catch(err) {
				echo "catch but let it go"
				env.userInput="Build"
				echo "${env.userInput}"
			}
		} //input

		if (userInput == 'Build') {

			stage ('Prepare for Build') {

				timeout(time: 30, unit: 'SECONDS') {
					buildType = input message: 'User input required', ok: 'Continue', 
					parameters: [choice(name: 'Select Build Type', choices: 'Dev Build\nGA Build', defaultValue: 'Dev Build', description: 'Dev or GA Build?')]
	    		}
	
				timeout(time: 30, unit: 'SECONDS') {
					userInputCodeSource = input message: 'User input required', ok: 'Continue', 
					parameters: [choice(name: 'Select Build Source', choices: 'trunk\nbranches', defaultValue: 'branches', description: 'trunk or branches?')]
	    		}
	    		
	    		if(userInputCodeSource == 'branches') {
	    			branchVersion = input(id: 'userInput', message: 'Enter Branch version number:?', 
	    				parameters: [
	    					string(
	    						defaultValue: '1.64.0',
	    						description: 'Branch',
	    						name: 'Branch_version_number'
	    					)
	    				])

					codeBuildJobParam = 'branches/' + 'ibo-ict-'+ branchVersion

				}
				echo "code to build from ${codeBuildJobParam}"
			} // end of 'Prepare for Build'

			stage('Code Build and SonarQube Analysis') {
				if (userInput == 'Build') {

					if (buildType == 'Dev Build') {

						buildPath = "DevBuild"

						// trigger 'Dev Build' Jenkins job with Sonarcube Analysis
						// TODO does this job has SonarQube configured?
						build job: 'ibo-app-docker-DevBuild', parameters: [[$class: 'StringParameterValue', name: 'source', value: "${codeBuildJobParam}"]]
						//withSonarQubeEnv('sonar') {
						//	sh "${maven} clean deploy -e -U -DskipTests=true sonar:sonar -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
						//	sh "svn info --show-item revision > svnrevision"
						//	svnrevision = readFile 'svnrevision'
						//	echo "svnrevision" + "${svnrevision}"
						//}
					}

					//General Availability Build
					if (buildType == 'GA Build') {

						buildPath = "GABuild"

						// trigger 'GA Build' Jenkins job with Sonarcube Analysis
						// TODO does this job has SonarQube configured?
						build job: 'ibo-app-docker-GABuild', parameters: [[$class: 'StringParameterValue', name: 'source', value: "${codeBuildJobParam}"]]
						//withSonarQubeEnv('sonar') {
						//	sh "${maven} clean deploy -e -U -DskipTests=true sonar:sonar -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
						//	sh "svn info --show-item revision > svnrevision"
						//	svnrevision = readFile 'svnrevision'
						//	echo "svnrevision" + "${svnrevision}"
						//}
					}
				} else {
					echo "Not doing a code build as the userInput is ${userInput} ..."
				}
			} // end of 'Code Build and SonarQube Analysis'

			envInput = "DEV" // deploy docker image to Dev env by default if userInput is a build request.

			dockerBuildStage() // docker image build stage
			deploymentStage()

		} else if (userInput == 'Docker Build') {
			echo "docker image build stage..."
			dockerBuildStage() // docker image build stage
		} else if (userInput == 'Promote') {
			echo "Promote stage..."
			deploymentStage()
		} else {
			echo "No User Input Action to proceed with Pipeline process..."
		}
	} catch (any) {
		echo "caught ${any}"
        currentBuild.result = 'FAILURE'
    } finally {
	
		if ((envlevel == "Production") && (currentBuild.result == "SUCCESS")){
			sendEmail(emailDistribution, envlevel, userInput, envInput)
		} else if((envlevel == "Production") && (currentBuild.result == "FAILURE")){
			sendEmail(emailDistribution, envlevel, userInput, envInput)
		} else{
			currentBuild.result = 'SUCCESS'
			sendEmail(emailDistribution, envlevel, userInput, envInput)
			//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
		}
	}
} //end node

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////FUNCTIONS//////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def dockerBuildStage() {

	stage ('Prepare for Docker Build') {

		timeout(time: 30, unit: 'SECONDS') {
			userInputDockerScriptSource = input message: 'User input required', ok: 'Continue', 
			parameters: [choice(name: 'Select Docker Scripts Source', choices: 'trunk\nbranches', defaultValue: 'branches', description: 'trunk or branches?')]
		}

		//userInputDockerScriptSource="trunk"
		echo "userInput for Docker Script Source ${userInputDockerScriptSource}"

		constructDockerBuildSVNPath()

		echo "Docker Script to export from ${finalSVNPath_Docker}"
		// export Docker Scripts
		sh "svn export ${finalSVNPath_Docker}"

		echo "after exporting docker build image script..."
		sh "ls -ltr"

		dir(dockerBuildScriptDir) {

			if(userInput == 'Docker Build') {

				if (buildType == "null" || buildType == "" ) {
					timeout(time: 30, unit: 'SECONDS') {
						buildType = input message: 'User input required', ok: 'Continue', 
						parameters: [choice(name: 'Select Build Type', choices: 'Dev Build\nGA Build', defaultValue: 'Dev Build', description: 'Dev or GA Build?')]
		    		}
				}

				if (buildType == 'Dev Build') {
					buildPath = "DevBuild"
				} else if (buildType == 'GA Build') {
					buildPath = "GABuild"
				}

    			//branchVersion = input(id: 'userInput', message: 'Enter IBO APP Branch version number:?', 
    			//	parameters: [
    			//		string(
    			//			defaultValue: '1.64.0',
    			//			description: 'Branch',
    			//			name: 'Branch_version_number'
    			//		)
    			//	])
			}

		    // export ibo.tgz file from Jenkin
			// buildPath could be DevBuild or GABuild
		    iboBuildFile = iboBuildFilePath_1 + buildPath + iboBuildFilePath_2
			
			//copyArtifacts filter: 'ibo.tgz', fingerprintArtifacts: true, projectName: 'ibo-app-docker-GABuild', selector: lastSuccessful()
			
			sh "wget --no-check-certificate -O ibo.tgz ${iboBuildFile}"
		    echo "list of Docker Script dir... "
			sh "ls -ltr"
		}
	}

	// build Docker Image
	stage ('Docker Build') {
		dir(dockerBuildScriptDir) {

			// build docker image
			imageTagVersion = input(id: 'userInput', message: 'Enter image Tag Version number:?', 
				parameters: [
					string(
						defaultValue: '1.64.0',
						description: 'Tag Image',
						name: 'imageTagVersion'
					)
				])

			echo "imageTagVersion is ${imageTagVersion}"
			sh "docker build -t docker.maven.incomm.com/iboapp:${imageTagVersion} -f Dockerfile --no-cache ."
			sh "docker images"
			// push image to repo
			sh "docker push docker.maven.incomm.com/iboapp:${imageTagVersion}"
			echo "image has been pushed..."
			// remove installed image to reclaim space.
			sh "docker rmi docker.maven.incomm.com/iboapp:${imageTagVersion}"
			echo "image has been removed..."
		}
	} // end of 'Docker Build'
}

def deploymentStage() {

	stage ('Prepare for Deployment') {

		echo "Preparing for deployment to ${envInput} environment..."

		//if(userInputDockerScriptSource == "") {
		//	timeout(time: 30, unit: 'SECONDS') {
		//		userInputDockerScriptSource = input message: 'User input required', ok: 'Continue', 
		//			parameters: [choice(name: 'Select Docker Scripts Source', choices: 'trunk\nbranches', defaultValue: 'branches', description: 'trunk or branches?')]
    	//	}
		//}

		userInputDockerScriptSource="trunk"
		constructDockerDeploySVNPath()
		echo "Docker Script to checkout from ${finalSVNPath_Docker} for Deployment..."

		// export Docker Scripts
	    sh "svn export ${finalSVNPath_Docker}"

		dir(dockerDeployScriptDir) {

			if(userInput == 'Promote') {

				if (buildType == "null" || buildType == "" ) {
					timeout(time: 30, unit: 'SECONDS') {
						buildType = input message: 'User input required', ok: 'Continue', 
						parameters: [choice(name: 'Select Build Type', choices: 'Dev Build\nGA Build', defaultValue: 'Dev Build', description: 'Dev or GA Build?')]
		    		}
				}

				if (buildType == 'Dev Build') {
					buildPath = "DevBuild"
				} else if (buildType == 'GA Build') {
					buildPath = "GABuild"
				}
			}

		    iboBuildFile = iboBuildFilePath_1 + buildPath + iboBuildFilePath_2
 
			// get ibo.tgz file from Jenkin
			//copyArtifacts filter: 'ibo.tgz', fingerprintArtifacts: true, projectName: 'ibo-app-docker-GABuild', selector: lastSuccessful()
			
			sh "wget --no-check-certificate -O ibo.tgz ${iboBuildFile}"
			sh "ls -ltr"

			dir(rpmExtractDir) {
				sh "tar xvzf ../ibo.tgz"
				sh "ls -ltr"
			}

		    echo "list of Docker Script deploy dir "
			sh "ls -ltr ${pwd}"
		}

		echo "imageTagVersion is ${imageTagVersion}"

		// user input to enter dokcer image tag
		if(imageTagVersion == "" || imageTagVersion == "null") {
			imageTagVersion = input(id: 'userInput', message: 'Enter IBO APP image tag value:?', 
				parameters: [
					string(
						defaultValue: '1.64.0',
						description: 'Docker Image Tag',
						name: 'image_tag'
					)
				])
		}

		if (envInput == 'DEV') {
			dockerDeploy_lowerEnv()
		} else {
			timeout(time: 30, unit: 'SECONDS') {
				envlevel = input message: 'User input required', ok: 'Continue', 
					parameters: [choice(name: 'Choose Production or Lower level environment?', choices: 'Lower\nProduction', description: 'Build or Promote?')]
			}

			echo "envlevel is ${envlevel}"

			// Deploy Docker Image 
			if (envlevel == 'Lower') {
				timeout(time: 30, unit: 'SECONDS') {
					envInput = input message: 'User input required', ok: 'Continue',
				 		parameters: [choice(name: 'Select Environment', choices: 'QA\nUAT', description: 'Promoting to ?')]
				}

				echo "envInput is ${envInput}"
				dockerDeploy_lowerEnv()

			} else if(envlevel == 'Production') {
				envInput = "PROD"
				target_env = envInput
				getApproval(approvalData)
				dockerDeploy_ProdEnv()
				currentBuild.result = 'SUCCESS'
			}
		}
	}
}

def dockerDeploy_lowerEnv() {
	
	echo "Deploying to ${envInput} environment..."

	if (envInput == 'DEV') {

		stage ('Deploying to DEV') {

			dir(dockerDeployScriptDir) {

				if(instanceInput == "null" || instanceInput == "") {
					timeout(time: 30, unit: 'SECONDS') {
						instanceInput = input message: 'User input required', ok: 'Continue',
				 			parameters: [choice(name: 'applicationInstanceName', choices: 'fdt01\nfdt02\nict01\nict02\nfdt_all\nict_all', description: 'Select Application Instance')]
					}
				}

				getRPMProperties()

				if ("${instanceInput}" == "fdt_all") {
					for (i = 0; i < dev_all_host_fdt.size(); i++) {

						hostname = "${dev_all_host_fdt[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_fdt_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}" == "ict_all") {
					for (i = 0; i < dev_all_host_ict.size(); i++) {

						hostname = "${dev_all_host_ict[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_ict_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}".contains("fdt") || "${instanceInput}".contains("ict")) {

					def hostName = "dev_host_${instanceInput}"
					echo "hostName value is ${hostName}"
					echo "Installing on ${hostMap[hostName]} ..."

					deployToHost("${hostMap[hostName]}")
				}
			}
		}
	} else if (envInput == 'QA') {

		stage ('Deploying to QA') {

			dir(dockerDeployScriptDir) {

				timeout(time: 30, unit: 'SECONDS') {
					instanceInput = input message: 'User input required', ok: 'Continue',
				 		parameters: [choice(name: 'Select Application Instance', choices: 'fdt01\nfdt02\nict01\nict02\nfdt_all\nict_all', description: 'Deploying to ?')]
				}

				getRPMProperties()

				if ("${instanceInput}" == "fdt_all") {
					for (i = 0; i < qa_all_host_fdt.size(); i++) {

						hostname = "${qa_all_host_fdt[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_fdt_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}" == "ict_all") {
					for (i = 0; i < qa_all_host_ict.size(); i++) {

						hostname = "${qa_all_host_ict[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_ict_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}".contains("fdt") || "${instanceInput}".contains("ict")) {

					def hostName = "qa_host_${instanceInput}"
					echo "hostName value is ${hostName}"
					echo "Installing on ${hostMap[hostName]} ..."

					deployToHost("${hostMap[hostName]}")
				}
			}
		}
	} else if (envInput == 'UAT') {

		stage ('Deploying to UAT') {

			dir(dockerDeployScriptDir) {

				timeout(time: 30, unit: 'SECONDS') {
					instanceInput = input message: 'User input required', ok: 'Continue',
				 		parameters: [choice(name: 'Select Application Instance', choices: 'fdt01\nfdt02\nict01\nict02\nfdt_all\nict_all', description: 'Deploying to ?')]
				}

				getRPMProperties()

				if ("${instanceInput}" == "fdt_all") {
					for (i = 0; i < uat_all_host_fdt.size(); i++) {

						hostname = "${uat_all_host_fdt[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_fdt_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}" == "ict_all") {
					for (i = 0; i < uat_all_host_ict.size(); i++) {

						hostname = "${uat_all_host_ict[i]}"
						echo "Installing on ${hostname} ..."

						instanceToDeploy="${instanceInput_ict_all_lle[i]}"
						echo "instance To Deploy on ${instanceToDeploy} ..."

						deployToHost(hostname)
					}
				} else if ("${instanceInput}".contains("fdt") || "${instanceInput}".contains("ict")) {

					def hostName = "uat_host_${instanceInput}"
					echo "hostName value is ${hostName}"
					echo "Installing on ${hostMap[hostName]} ..."

					deployToHost("${hostMap[hostName]}")
				}
			}
		}
	} else {
		echo "No env value provided ${envInput}"
	}
}

def getRPMProperties() {
	echo "get and update RPM Properties..."
	echo "current dir is--- "
	sh "ls -ltr"

	envInput_lowerCase = envInput.toLowerCase()
	echo "envInput_lowerCase is ${envInput_lowerCase}"
	echo "rpmExtractDir is ${rpmExtractDir}"
	echo "instanceInput is ${instanceInput}"

	if(instanceInput == 'fdt_all') {
		for (i = 0; i < instanceInput_fdt_all_lle.size(); i++) {
			sh "cp ${rpmExtractDir}/rpm/${envInput_lowerCase}.ibo_${instanceInput_fdt_all_lle[i]}.properties ibo_${envInput_lowerCase}_${instanceInput_fdt_all_lle[i]}.properties"
		}
	} else if(instanceInput == 'ict_all') {
		for (i = 0; i < instanceInput_ict_all_lle.size(); i++) {
			sh "cp ${rpmExtractDir}/rpm/${envInput_lowerCase}.ibo_${instanceInput_ict_all_lle[i]}.properties ibo_${envInput_lowerCase}_${instanceInput_ict_all_lle[i]}.properties"
		}
	} else {
		if(envlevel == 'Production') {
			envInput_lowerCase = 'prd'
			for (i = 0; i < instance_Prod.size(); i++) {
				echo "instance_Prod ${instance_Prod[i]}"
				sh "cp ${rpmExtractDir}/rpm/${envInput_lowerCase}.ibo_${instance_Prod[i]}.properties ibo_${envInput_lowerCase}_${instance_Prod[i]}.properties"
			}
		} else {
			sh "cp ${rpmExtractDir}/rpm/${envInput_lowerCase}.ibo_${instanceInput}.properties ibo_${envInput_lowerCase}_${instanceInput}.properties"				
		}
	}

	sh "cat ${rpmExtractDir}/rpm/revision.properties | tr . _ > revision.properties"
	sh "cat ${rpmExtractDir}/rpm/version.properties | tr . _ > version.properties"

	echo "files folder under ${rpmExtractDir}/rpm/"
	sh "ls -ltr ${rpmExtractDir}/rpm/"
}

def dockerDeploy_ProdEnv() {
	stage ('Deploying to PROD') {

		dir(dockerDeployScriptDir) {

			getRPMProperties()

			for (i = 0; i < prod_host_all.size(); i++) {

				hostname = "${prod_host_all[i]}"
				echo "Installing on ${hostname} ..."

				instanceToDeploy="${instance_Prod[i]}"
				echo "instance To Deploy on ${instanceToDeploy} ..."

				deployToHost(hostname)
			}
		}
	}
}

def constructDockerBuildSVNPath() {
	if(userInputDockerScriptSource == 'branches') {
		dockerBranchVersion = input(id: 'userInputForDocker', message: 'Enter Branch name:?', 
			parameters: [
				string(
					defaultValue: '8.0.0',
					description: 'Branch',
					name: 'Docker_Branch_version_name'
				)
			])

		finalSVNPath_Docker = developmentBranch_Docker + dockerBranchVersion + "/docker-ibo-app-pipeline/build-docker-image/"
	} else {
		finalSVNPath_Docker = releaseTrunk_Docker + "/build-docker-image/"
	}
}

def constructDockerDeploySVNPath() {

	echo "constructing Docker Deploy SVN Path..."

	if(userInputDockerScriptSource == 'branches') {
		dockerBranchVersion = input(id: 'userInputForDocker', message: 'Enter Branch version number:?', 
			parameters: [
				string(
					defaultValue: '8.0.0',
					description: 'Branch',
					name: 'Docker_Branch_version_number'
				)
			])

		finalSVNPath_Docker = developmentBranch_Docker + dockerBranchVersion + "/docker-ibo-app-pipeline/deploy-docker-image/"
	} else { 
		finalSVNPath_Docker = releaseTrunk_Docker + "/deploy-docker-image/"
	}

	echo "finalSVNPath_Docker is $finalSVNPath_Docker"
}

def deployToHost(hostname_param) {

	echo "hostname_param is ${hostname_param}"
	echo "imageTagVersion is ${imageTagVersion}"

	if("null" == instanceToDeploy) {
		instanceToDeploy = instanceInput
	}

	echo "instanceInput is ${instanceInput}"
	echo "instanceToDeploy is ${instanceToDeploy}"
	echo "files under dir"

	sh "ls -ltr"

	// stop the container
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker stop ibo_app' || true"
	//sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker rm -f \$(docker ps -a -q -f name=ibo_app)' || true"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker rm -f \$(docker ps -a -q)' || true"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker rmi \$(docker images -q)' || true"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker rmi \$(docker images -qf dangling=true)' || true"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} '/bin/rm -rf /home/deploy/ibo_app_config'"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'mkdir -p /home/deploy/ibo_app_config' || true"
	sh "scp -i ~/.ssh/pipeline update_configuration.sh root@${hostname_param}:/home/deploy/ibo_app_config/"
	sh "scp -i ~/.ssh/pipeline ibo_${envInput_lowerCase}_${instanceToDeploy}.properties root@${hostname_param}:/home/deploy/ibo_app_config/ibo_app.properties"
	sh "scp -i ~/.ssh/pipeline version.properties root@${hostname_param}:/home/deploy/ibo_app_config/version.properties"
	sh "scp -i ~/.ssh/pipeline revision.properties root@${hostname_param}:/home/deploy/ibo_app_config/revision.properties"

	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'chmod -R 777 /home/deploy/ibo_app_config'"
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'chown -R deploy:deploy /home/deploy'"
	echo "chown chmod"
	// pull required docker image from registry 
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker pull docker.maven.incomm.com/iboapp:${imageTagVersion}'"

	// start the container
	sh "ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${hostname_param} 'docker run --name ibo_app -v /home/deploy/ibo_app_config:/opt/ofbiz/ibo_app/ibo_app_config --mount type=bind,source=/var/opt/app_logs/ibo_app,destination=/opt/ofbiz/ibo_app/runtime/logs --net=host -d docker.maven.incomm.com/iboapp:${imageTagVersion}'"
}