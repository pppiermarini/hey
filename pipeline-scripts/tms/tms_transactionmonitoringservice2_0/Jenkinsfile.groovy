import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import java.text.SimpleDateFormat
import java.lang.*


//cfes-tms_transaction_monitoring_v2
@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk
// do
// Branches
gitRepo="https://github.com/InComm-Software-Development/cfes-tms-fraudmonitoringv2.git"
qaBranch="null"
releaseBranch="null"
gitCreds="scm-incomm"
gitBranch="sprint"

emailDistribution="ppiermarini@incomm.com"
//

pipelineData="/app/pipeline-data/cfes-tms-transactionmonitoringservice2.0"
//

artifactDeploymentLoc="/var/opt/pivotal/pivotal-tc-server/tms-instance/webapps/"
libDeploymentLoc="/var/opt/pivotal/pivotal-tc-server-standard/tms-instance/lib/"
serviceName="tms-instance"
pipeline_id="${env.BUILD_TAG}"
srcArtifactLocation="${env.WORKSPACE}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

//Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.services.tms'
artifactId ='tms-service-webapp'
env_propertyName ='ART_VERSION'
artExtension ='war'
artifactName ='tms-service-v2.x.war'


//globals
userApprove="null"
userInput="null"
envInput="null"
envlevel="null"
localArtifact="null"
svnrevision="null"
relVersion="null"



userInput = InputAction()
echo "Lib User Input = $userInput"
	
	

node('linux1') {
	stage('checkout'){
	
	if(userInput != 'Promote') {
		timeout(time: 50, unit: 'SECONDS') {
		echo "Initializing branch listing from repo"
		echo gitRepo
		git url: gitRepo, credentialsId: gitCreds
		sh "git branch -r | awk \'{print \$1}\' ORS=\'\\n\' > branches.txt"
		sh """cut -d '/' -f 2 branches.txt >> branch.txt"""
		sh "cat branch.txt"

		liste = readFile "branch.txt"
		echo "please click on the link here to chose the branch to build"
		//echo "liste = $liste"
		gitBranch = input message: "Please choose the branch to build ", ok: "Continue",
		parameters: [choice(name: 'BRANCH_NAME', choices: liste, description: "Branch to build?")]
		
		echo "creds= $gitCreds "
		echo "GitRepo= $gitRepo"
		echo "gitBranch= $gitBranch"
		echo ""
		}
	} else if (userInput == 'Promote') {
		// checkout functions use to be here.
		timeout(time: 50, unit: 'SECONDS') {
		echo "Initializing branch listing from repo"
		echo gitRepo
		git url: gitRepo, credentialsId: gitCreds
		sh "git branch -r | awk \'{print \$1}\' ORS=\'\\n\' > branches.txt"
		sh """cut -d '/' -f 2 branches.txt >> branch.txt"""
		sh "cat branch.txt"

		liste = readFile "branch.txt"
		echo "please click on the link here to chose the branch or tag to get your property files from"
		//echo "liste = $liste"
		gitBranch = input message: "branch or tag to get your property files from ", ok: "Continue",
		parameters: [choice(name: 'BRANCH_NAME', choices: liste, description: "Branch to build?")]
		
		echo "creds= $gitCreds "
		echo "GitRepo= $gitRepo"
		echo "gitBranch= $gitBranch"
		echo ""
		}
	}

	}
} //end node for checkout

node('linux1'){
	
			cleanWs()
		if ((userInput == 'Build')||(userInput == 'Release')){
			githubCheckout(gitCreds,gitRepo,gitBranch)
		} else if (userInput == 'Promote') {
			githubCheckout(gitCreds,gitRepo,gitBranch)
		} else {
			echo "Not doing a Checkout"
		}
}


node('linux1') {
	try{
	stage('Build and SonarQube Analysis'){
		
		jdk=tool name:"${javaVersion}"
		env.JAVA_HOME="${jdk}"
		echo ""
		echo ""
		echo "jdk installation path is: ${jdk}"
		sh "${jdk}/bin/java -version"
		echo ""
		
			if((userInput != 'Promote') && (userInput != 'Release') && (userInput != 'QABuild')){
				withSonarQubeEnv('sonarqube.incomm.com'){
				//sh "${maven} -f pom.xml -f -e -U sonar:sonar -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dsonar.login=eb1e18aebfc5dd34e035f991085681da71390085 -Dsonar.host.url=https://sonar.incomm.com"
				sleep (3)
				sh "${maven} clean deploy -e -U -DskipTests sonar:sonar -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

				}
			} else if (userInput == 'Release') {
			
				echo "Maven Release Build"

				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"

				sh"${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				
				sh"${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -DuseReleaseProfile=false"
				sleep(4)

				str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				sh "/bin/cp -f mavenrelease /app/pipeline-data/tms_transaction_monitoring_v2/dev"

			} else if (userInput == 'QABuild') {	
			
				withSonarQubeEnv('sonar'){
				sh "${maven} clean deploy -e -U -Drevision=-DskipTests sonar:sonar -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

				}
				echo "Sonar Analysis"
					
			}else {
				echo "Promote Sequence"
			}				

	} //stage  Build and SonarQube Analysis
	
	
stage('SonarQube Quality Gate Check') {
		if((env.userInput == 'Build') || (env.userInput == 'QABuild')){
			//dir('.') {
			//	withSonarQubeEnv('Local_SonarQube_Server') {
			//		powershell 'mvn sonar:sonar'
			//	}
			//}
			
		//	println "Sonar analysis Quality Gate not working"
			
			sleep(3)
			//timeout(time: 15, unit: 'MINUTES') {
			//	script {
			//		def qg = waitForQualityGate()
			//		if(qg.status != 'OK'){
			//			error "Pipeline aborted due to Qaulity gate failure: ${qg.status}"
			//		}
			//	}
			//}
			
		} else {
		
			echo "Promote Sequence or Release"
		}
}//end QualityGate
	
	
stage('LowerLevel or Production'){
	def result
		switch(userInput) {
		  case "Build":
			envInput = "DEV"
			envlevel = "Lower"
			result = "Build"
			break
		  case "QABuild":
			envInput = "QA"
			envlevel = "Lower"
			result = "QABuild"
			break
		  case "Release":
			//envInput = "QA"
			//envlevel = "Lower"
			result = "Release"
			break
		  default:
			result = "Promote"
			break
		}
		echo "result = " + "$result"
		
		if (result == "Promote"){
			timeout(time: 30, unit: 'SECONDS') {
			 envlevel = input message: 'User input required', ok: 'Continue',
				parameters: [choice(name: 'Choose Production or lower level environment?', choices: 'Lower\nProduction', description: 'Build or Promote?')]
			} 		
		}

}




if (userInput != "Release"){
	stage('Enter Environment'){
		

		echo "envlevel= " + "${envlevel}"
		if ((userInput == 'Promote') && (envlevel == 'Production')){
			
				timeout(time: 30, unit: 'SECONDS') {
				envInput = input message: 'User input required', ok: 'Continue',
					 parameters: [choice(name: 'Select Environment', choices: 'PROD_01\nPROD_02\nPROD_03\nPROD_04\nPROD_05\nPROD_06\nPROD_07\nPROD_08\nPROD_09\nPROD_10\nPROD_11\nPROD_12', description: 'Promoting to ?')]
				}	
				
		} else if ((userInput == 'Promote') && (envlevel == 'Lower')){
			
			timeout(time: 30, unit: 'SECONDS') {
				envInput = input message: 'User input required', ok: 'Continue',
					 parameters: [choice(name: 'Select Environment', choices: 'QA\nLOD', description: 'Promoting to ?')]
				}			
				
		} else {
			
		echo "nada"
		}
			echo "Selected Environment ${envInput}"
			
			if(envInput == "DEV"){
				ARTIFACT()
				DEV()
				DEVTesting()

			}
			if(envInput == "DEV_DELTA"){
				ARTIFACT()
				DEV()
				DEVTesting()

			}
			if(envInput == "DEV_TARGET"){
				ARTIFACT()
				DEV()
				DEVTesting()

			}
			if(envInput == "QA"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()

			}
			if(envInput == "PROD_01"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()

			}
			if(envInput == "PROD_02"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()

			}
			if(envInput == "PROD_03"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				
			}
			if(envInput == "PROD_04"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				
			}
			if(envInput == "PROD_05"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				
			}
			if(envInput == "PROD_06"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()

			}
			if(envInput == "PROD_07"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				
			}
			if(envInput == "PROD_08"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				PROD_08()
			
			}
			if(envInput == "PROD_09"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				PROD_08()
				PROD_09()
				
			}
			if(envInput == "PROD_10"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				PROD_08()
				PROD_09()
				PROD_10()
				
			}
			if(envInput == "PROD_11"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				PROD_08()
				PROD_09()
				PROD_10()
				PROD_11()
				
			}
			if(envInput == "PROD_12"){
				ARTIFACT()
				DEV()
				DEVTesting()
				QA()
				QATesting()
				Approve()
				PROD_01()
				PROD_02()
				PROD_03()
				PROD_04()
				PROD_05()
				PROD_06()
				PROD_07()
				PROD_08()
				PROD_09()
				PROD_10()
				PROD_11()
				PROD_12()
				
			}
			
	} //end environment stage
} else {
	stage('Enter Environment'){
		
	echo "Release Build Completed"
	echo "Use Promote to deploy the Release"
	envInput = "Use Promote to deploy the Release"
	}
}	
	
		
	} catch (any) {
		echo "try and catch me"
        currentBuild.result = 'FAILURE'
		
    } finally {
	
	if ((envlevel == "Production") && (userApprove == "Approved")){
		currentBuild.result = 'SUCCESS'

		sendEmail(emailDistribution, envlevel, userInput, envInput, userApprove)

	}else if((envlevel == "Production") && (userApprove == "NotApproved")){
		
		sendEmail(emailDistribution, envlevel, userInput, envInput, userApprove)
		//currentBuild.result = 'ABORTED'
	}else{
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



def Approve(){

stage('Approval'){
	echo "zero " + "${userInput}"
	
	if ((userInput == "Promote") && (envlevel == "Production")){

			timeout(time: 2, unit: 'MINUTES') {
				userApprove = input message: 'User input required', ok: 'Continue',
					parameters: [choice(name: 'Choose Build Option?', choices: 'Approved\nNotApproved', description: 'Build or Promote?')]
			}
			
			echo "one " + "${userApprove}"
		
		if ((envlevel == "Production") && (userApprove != "Approved")){
			echo "Approval Denied"
			currentBuild.result = 'ABORTED'
			//error('Stopping early…')  causes view to crumble
		}else{
		echo "Deployment was Approved"
		}

	}else{
	echo "Approval not needed"
	}
	
} //approval stage
}

def ARTIFACT(){
stage('Select Artifact to be Promoted'){
	if (userInput == "Promote"){
	echo "Select an artifact from Artifacory"
	
	relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
	} else {
	echo "not promoting-Skipping"
	}	
		
}
}

def DEV() {
stage('Deploy To DEV'){
//sdddsapp452V
String envName="DEV"
if(envInput == "${envName}"){

		echo "Promoting to ${envName}"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["sdtmsapp01v", "sdtmsapp02v", "sdtmsapp03v", "sdtmsapp04v"]
		echo "sdtmsapp01v, sdtmsapp02v, sdtmsapp03v, sdtmsapp04v"
		
		//echo "read the pom"
		pom = readMavenPom file: 'pom.xml'
		relVersion = pom.getVersion();
		echo "relVersion=  ${relVersion}"
		writeFile file: "relVersion", text: relVersion
		echo "writefile ${relVersion}"
		sh "/bin/cp -f relVersion ${pipelineData}/dev"
		
		sh "/bin/cp tms-service-webapp/target/tms-service-v2.x.war ."
		
		remoteArtifact="tms-service-v2.x.war"
		localArtifact="tms-service-v2.x.war"
		tmsProp="tms.properties"
		mdmclientProp="mdm-client.properties"
		log4jXml="log4j.xml"
		echo "local artifact=  $localArtifact"
		echo "remote artifact=  ${remoteArtifact}"
		echo "tms Prop= $tmsProp"
		echo "mdm client Prop= $mdmclientProp"
		echo "log Xml= $log4jXml"
		
		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "DEV" +" p${HOSTNAME[i]}p"
				echo "${serviceName}"
				// stop service
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
				sleep(8)
				
				echo "remove the old war"
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
				sleep(1)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
				sleep(3)
				
				echo "copying property files on server"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/dev/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/dev/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/dev/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				
				echo "copying tms-service-${relVersion}.war...."
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"
				sleep(3)

			def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,remoteArtifact,localArtifact)
			echo "validate=  $validate"
				if("${validate}" != "0"){
				echo "files are different 1"
				currentBuild.result = 'ABORTED'
				error('Files do not match...')
				}else{
				echo "files are the same 0"
				}				
			
				
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
				sleep(8)
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
			println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
			echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		sleep (2)
	    echo "${envName} Environment Deployed"
	}else{

	    echo "${envName} not deployed"
	}
}
} // end of dev


//#############################################################################################
def QA() {
// (QA)  
stage('Deploy To QA'){
def envName="QA"
	if(envInput == "${envName}"){
		echo "Promoting to ${envName}"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["sqltmsapp01v", "sqltmsapp02v", "sqs00tmsapp01v", "sqs00tmsapp02v"]
		echo "sqltmsapp01v, sqltmsapp02v, sqs00tmsapp01v, sqs00tmsapp02v"

		echo "${relVersion}"
		writeFile file: "relVersion", text: relVersion
		echo "writefile ${relVersion}"
		sh "/bin/cp -f relVersion ${pipelineData}/qa"

		remoteArtifact="tms-service-v2.x.war"
		localArtifact="tms-service-v2.x.war"
		tmsProp="tms.properties"
		mdmclientProp="mdm-client.properties"
		log4jXml="log4j.xml"
		echo "local artifact=  $localArtifact"
		echo "remote artifact=  ${remoteArtifact}"
		echo "tms Prop= $tmsProp"
		echo "mdm client Prop= $mdmclientProp"
		echo "log Xml= $log4jXml"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "QA" +" p${HOSTNAME[i]}p"
				echo "${serviceName}"
				// stop service
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
				sleep(8)
				
				echo "remove the old war"
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
				sleep(1)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
				sleep(3)
				
				echo "copying property files on server"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/qa/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/qa/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/qa/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
				
				echo "copying tms-service-${relVersion}.war...."
				sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"
				sleep(3)

			def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,remoteArtifact,localArtifact)
			echo "validate=  $validate"
				if("${validate}" != "0"){
				echo "files are different 1"
				currentBuild.result = 'ABORTED'
				error('Files do not match...')
				}else{
				echo "files are the same 0"
				}				
			
				
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
				sleep(8)
				
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
			println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
			echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		sleep (2)
	    echo "${envName} Environment Deployed"
	}else{

	    echo "${envName} not deployed"
	}
}
} //end of QA

//###################################################################

def PROD_01() {
	stage('Deploy To PROD_01'){
	def envName="PROD_01"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_01"
	// prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
				if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.104"]
				//echo "SPTMSAPP01v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

				
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
					println HOSTNAME[i]
					echo "QA" +" p${HOSTNAME[i]}p"
					echo "${serviceName}"
					// stop service
					
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(8)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					
					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"
					sleep(3)

				def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,remoteArtifact,localArtifact)
				echo "validate=  $validate"
					if("${validate}" != "0"){
					echo "files are different 1"
					currentBuild.result = 'ABORTED'
					error('Files do not match...')
					}else{
					echo "files are the same 0"
					}				
			
			
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
					sleep(8)
						
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
				println "inside loop"
				}
				println "outside loop"
			} 
			catch(Exception){
				echo "caught exception"
						throw new AbortException("${job.fullDisplayName} aborted.")
			}

			sleep (2)
			echo "${envName} Environment Deployed"

		}else{

			echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_01

//#############################################################################################

def PROD_02() {
stage('Deploy To PROD_02'){
def envName="PROD_02"
	if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_02"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
		if(envInput == "${envName}"){
			echo "Promoting to ${envName}"
			// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
			String[] HOSTNAME = ["10.40.5.108"]
			echo"SPTMSAPP02v "
			
			echo "${relVersion}"
			writeFile file: "relVersion", text: relVersion
			echo "writefile ${relVersion}"
			sh "/bin/cp -f relVersion ${pipelineData}/qa"
			
			remoteArtifact="tms-service-v2.x.war"
			localArtifact="tms-service-v2.x.war"
			tmsProp="tms.properties"
			mdmclientProp="mdm-client.properties"
			log4jXml="log4j.xml"
			echo "local artifact=  $localArtifact"
			echo "remote artifact=  ${remoteArtifact}"
			echo "tms Prop= $tmsProp"
			echo "mdm client Prop= $mdmclientProp"
			echo "log Xml= $log4jXml"

		
			try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_02
		
//#############################################################################################

def PROD_03() {
stage('Deploy To PROD_03'){
def envName="PROD_03"
	if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_03"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
		if(envInput == "${envName}"){
			echo "Promoting to ${envName}"
			// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
			String[] HOSTNAME = ["10.40.5.110"]
			echo "SPTMSAPP03v"
			
			echo "${relVersion}"
			writeFile file: "relVersion", text: relVersion
			echo "writefile ${relVersion}"
			sh "/bin/cp -f relVersion ${pipelineData}/qa"
			
			remoteArtifact="tms-service-v2.x.war"
			localArtifact="tms-service-v2.x.war"
			tmsProp="tms.properties"
			mdmclientProp="mdm-client.properties"
			log4jXml="log4j.xml"
			echo "local artifact=  $localArtifact"
			echo "remote artifact=  ${remoteArtifact}"
			echo "tms Prop= $tmsProp"
			echo "mdm client Prop= $mdmclientProp"
			echo "log Xml= $log4jXml"

			try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_03
		
//##################################################################################################################

def PROD_04() {
	stage('Deploy To PROD_04'){
	def envName="PROD_04"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_04"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.111"]
				echo"SPTMSAPP04v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_04

//#####################################################################################################

def PROD_05() {
	stage('Deploy To PROD_05'){
	def envName="PROD_05"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_05"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.112"]
				echo "SPTMSAPP05v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_05

//#####################################################################################################################

def PROD_06() {
	stage('Deploy To PROD_06'){
	def envName="PROD_06"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_06"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.113"]
				echo "SPTMSAPP06v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_06

//###################################################################################################################

def PROD_07() {
	stage('Deploy To PROD_07'){
	def envName="PROD_07"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_07"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.40"]
				echo "SPTMSAPP07v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_07

//#########################################################################################################

def PROD_08() {
	stage('Deploy To PROD_08'){
	def envName="PROD_08"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_08"
	///prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.5.83
	//SPTMSAPP10v 10.40.5.84
	//SPTMSAPP11v 10.40.5.85
	//SPTMSAPP12v 10.40.5.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.5.41"]
				echo "SPTMSAPP08v "
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_08

//#####################################################################################################################################

def PROD_09() {
	stage('Deploy To PROD_09'){
	def envName="PROD_09"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_09"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.7.83
	//SPTMSAPP10v 10.40.7.84
	//SPTMSAPP11v 10.40.7.85
	//SPTMSAPP12v 10.40.7.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.7.83"]
				echo "SPTMSAPP09v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_09

//#####################################################################################################################################

def PROD_10() {
	stage('Deploy To PROD_10'){
	def envName="PROD_10"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_10"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.7.83
	//SPTMSAPP10v 10.40.7.84
	//SPTMSAPP11v 10.40.7.85
	//SPTMSAPP12v 10.40.7.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.7.84"]
				echo "SPTMSAPP10v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_10

//#####################################################################################################################################

def PROD_11() {
	stage('Deploy To PROD_11'){
	def envName="PROD_11"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_11"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.7.83
	//SPTMSAPP10v 10.40.7.84
	//SPTMSAPP11v 10.40.7.85
	//SPTMSAPP12v 10.40.7.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.7.85"]
				echo "SPTMSAPP11v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_11

//#####################################################################################################################################

def PROD_12() {
	stage('Deploy To PROD_12'){
	def envName="PROD_12"
		if ((envInput == "${envName}") && (userApprove == "Approved")){
		echo "PROD_12"
	//prod servers
	//SPTMSAPP01v 10.40.5.104
	//SPTMSAPP02v 10.40.5.108
	//SPTMSAPP03v 10.40.5.110
	//SPTMSAPP04v 10.40.5.111
	//SPTMSAPP05v 10.40.5.112
	//SPTMSAPP06v 10.40.5.113
	//SPTMSAPP07v 10.40.5.40
	//SPTMSAPP08v 10.40.5.41
	//SPTMSAPP09v 10.40.7.83
	//SPTMSAPP10v 10.40.7.84
	//SPTMSAPP11v 10.40.7.85
	//SPTMSAPP12v 10.40.7.86
			if(envInput == "${envName}"){
				echo "Promoting to ${envName}"
				// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
				String[] HOSTNAME = ["10.40.7.86"]
				echo "SPTMSAPP12v"
				
				echo "${relVersion}"
				writeFile file: "relVersion", text: relVersion
				echo "writefile ${relVersion}"
				sh "/bin/cp -f relVersion ${pipelineData}/qa"
				
				remoteArtifact="tms-service-v2.x.war"
				localArtifact="tms-service-v2.x.war"
				tmsProp="tms.properties"
				mdmclientProp="mdm-client.properties"
				log4jXml="log4j.xml"
				echo "local artifact=  $localArtifact"
				echo "remote artifact=  ${remoteArtifact}"
				echo "tms Prop= $tmsProp"
				echo "mdm client Prop= $mdmclientProp"
				echo "log Xml= $log4jXml"

		
				try {
				
					for (i = 0; i <HOSTNAME.size(); i++) {
						println HOSTNAME[i]

					echo "${serviceName}" + "${relVersion}"

					echo "stopping the service for $serviceName"

					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service $serviceName stop > /dev/null'"""
					sleep(6)
					
					echo "remove the old war"
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/${remoteArtifact}'"""
					sleep(1)
					sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} 'rm -rfv ${artifactDeploymentLoc}/tms-service-v2.x'"""
					sleep(3)
					
					echo "copying property files on server"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${tmsProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${mdmclientProp} root@${HOSTNAME[i]}:${libDeploymentLoc}/"
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no configurations/prod/qts-prod/${log4jXml} root@${HOSTNAME[i]}:${libDeploymentLoc}/"

					echo "copying tms-service-${relVersion}.war...."
					sh "scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${localArtifact} root@${HOSTNAME[i]}:${artifactDeploymentLoc}/${remoteArtifact}"

					sleep(3)


					def validate = md5("${HOSTNAME[i]}",artifactDeploymentLoc,localArtifact,remoteArtifact)
					echo "validate=  $validate"
					if("${validate}" != "0"){
						echo "files are different 1"
						currentBuild.result = 'ABORTED'
						error('Files do not match...')
						}else{
						echo "files are the same 0"
						}
						
						echo "starting the service for ${artifactId}.${artExtension}"

						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName start"""
						sleep(8)
						
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service $serviceName status"""
					println "inside loop"
					}
					println "outside loop"
				} catch(Exception){
					echo "caught exception"
							throw new AbortException("${job.fullDisplayName} aborted.")
				}

				sleep (2)
				echo "${envName} Environment Deployed"

			}else{

				echo "${envName} not deployed"
			}
		}
	}
} //end of PROD_12

//#####################################################################################################################################

def DEVTesting(){
		stage('Dev smoke Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "DEV Smoke Testing"
			sleep(1)
				dir('testresults'){
					//println "Run Test Script"
					//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
					// String results = readFile 'testresults.xml'
				}
				//if(1 ){
				//	println "ERROR todo "
				//} else {
				//	println "results"
				//}
			}else {
			echo "Promote Sequence"
			}
		} //end dev testing
		
}//end testing

def QATesting(){
		stage('QA Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "QA Smoke and Regression Testing"
			sleep(1)
				dir('testresults'){
					//println "Run Test Script"
					//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
					// String results = readFile 'testresults.xml'
				}
				//if(1 ){
				//	println "ERROR todo "
				//} else {
				//	println "results"
				//}
			}else {
			echo "Promote Sequence "
			}
		} //end qa testing
		
}//end testing