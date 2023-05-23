import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException


pipelineData = "E:\\pipeline-data\\itrac-web"

node('windows') {

    def pipeline_id="${env.BUILD_TAG}"
    def srcArtifactLocation="${env.WORKSPACE}"
	def svnrevision="null"
	def folder="null"
	
	stage('input'){
		try{
			timeout(time: 30, unit: 'SECONDS') {
				env.userInput = input message: 'User input required', ok: 'Continue',
						parameters: [choice(name: 'Build dev or Promote?', choices: 'Build\nPromote\nRollback', defaultValue: Build, description: 'Build or Promote?')]
			}
			echo "${env.userInput}"
			echo "selection"
		} catch(err) {

			env.userInput="Build"
			echo "${env.userInput}"
		} 
	} //input


	try {

		stage('Build Preparation') {
		if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			cleanWs()

			checkout changelog: false, poll: true, scm: [$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[credentialsId: '7a6d9c35-019c-485e-9b11-b92dcc3e4866', depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: 'https://svn.incomm.com/svn/devel/itrac/itrac/trunk']], workspaceUpdater: [$class: 'CheckoutUpdater']]
			
				//capture the svn revision to file
				bat "svn info --show-item revision > svnrevision.txt"
				svnrevision = readFile 'svnrevision.txt'
				echo "svnrevision" + "${svnrevision}"
				
			dir('.') {
			echo "workspace = ${env.WORKSPACE}"

				echo "E:\\opt\\apache-maven-3.2.1\\bin\\mvn clean deploy -e -U -Drevision=-DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
				bat "E:\\opt\\apache-maven-3.2.1\\bin\\mvn clean deploy -e -U -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
					//junit '**/surefire-reports/*.xml'
					junit '**/target/surefire-reports/*.xml'
					}
		}else {
			echo "Promote Sequence"
		}
			
		} //end build preperation

		/*stage('SonarQube Analysis'){
		if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
				bat "E:\\opt\\apache-maven-3.2.1\\bin\\mvn -f pom.xml -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
				//bat "E:\\opt\\apache-maven-3.2.1\\bin\\mvn -f pom.xml -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -e -B sonar:sonar -Dsonar.login=c19ded42-8902-4c86-8af3-9f19edac225c -Dsonar.host.url=https://sonarqube.incomm.com"
				println "Sonar Analysis"
		}else {
			echo "Promote Sequence"
		}				
		
		} */ 
		//end Sonarqube
						
		//stage('SonarQube Analysis & Quality Gate Check') {
		//if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			//dir('.') {
			//	withSonarQubeEnv('Local_SonarQube_Server') {
			//		powershell 'mvn sonar:sonar'
			//	}
			//}
			
		//	println "Sonar analysis Quality Gate not working yet"
			
		//	sleep(3)
			//timeout(time: 15, unit: 'MINUTES') {
			//	script {
			//		def qg = waitForQualityGate()
			//		if(qg.status != 'OK'){
			//			error "Pipeline aborted due to Qaulity gate failure: ${qg.status}"
			//		}
			//	}
			//}
		//} else {
		//	echo "Promote Sequence"
		//}
		//end QualityGate


	stage('Enter Environment'){
	if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
		env.envInput = "DEV"
		echo "Bullwinkle"
		echo "${env.userInput}"
		echo "${env.envInput}"
		
	} else {
		//	timeout(time: 30, unit: 'SECONDS') {
		//		env.envInput = input message: 'User input required', ok: 'Continue',
		//				parameters: [choice(name: 'Build dev or Promote?', choices: 'QA\nSTG\nUAT', defaultValue: QA, description: 'Build or Promote?')]
		//	}
		timeout(time: 30, unit: 'SECONDS') {
			env.envInput = input message: 'User input required', ok: 'Continue',
				 parameters: [choice(name: 'Select Environment', choices: 'QA\nSTG\nUAT', description: 'Promoting to ?')]
			}

			echo "${env.envInput}"
		
		}
		
		echo ("Env: "+env.envInput)
		DEV()
		DEVTesting()
		QA()
		QATesting()
		UAT()
		UATTesting()
		STG()
		STGTesting()
	} //end environment stage
		
		
		currentBuild.result = 'SUCCESS'

	} catch (any) {
	
        currentBuild.result = 'FAILURE'
		
    } finally {
	
        println 'Pipeline job: ' + pipeline_id + ' is a ' + currentBuild.result
		
        step([$class: "Mailer", notifyEveryUnstableBuild: true, recipients: "djensen@incomm.com", sendToIndividuals: true])
	mail bcc: "", body: "STATUS: ${currentBuild.result}\n\nAction: ${env.userInput}\nEnv: ${env.envInput}\n\nJOB Name: ${env.JOB_BASE_NAME}\nJob Tag: ${env.BUILD_TAG} \n\nTo view the results Check console output:\n ${env.BUILD_URL}\n\nSonar URL\nhttps://sonar.incomm.com/dashboard/index/com.incomm.itrac:itrac", cc: "", from: "pipeline@incomm.com", replyTo: "", subject: "${env.userInput} ${env.JOB_BASE_NAME} - Build # ${env.BUILD_TAG} to ${env.envInput} environment:  ${currentBuild.result}", to: "ppiermarini@incomm.com djensen@incomm.com"
	}
echo "${env.envInput}"

}//node

//#############################################################################


def DEV() {
	echo "${env.userInput}"

	if(env.envInput == 'DEV'){
		stage ('Deploy to DEV')
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["SDITRWEB181V"]
		String serviceName = "JBossEAP71"
		String devrelease = "null"
		String rollbackversion = "null"

			echo "DEPLOYING TO DEV"
			//cleanWs()
			//get the version
			echo "read the pom"
			pom = readMavenPom file: 'web\\pom.xml'
			devrelease = pom.getVersion();
			echo "${devrelease}" + "pomfile"
			

			// wget the requested file from artifacoty 
			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${devrelease}/web-${devrelease}.war"
			writeFile file: "devrelease.txt", text: devrelease
			echo "writefile ${devrelease}"
			bat "copy /Y devrelease.txt ${pipelineData}\\dev"
		

		echo "${serviceName}" + "service"
		echo "${devrelease}" + "dev release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]

				echo "${serviceName}" + "${devrelease}"
					// stop service
					//bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Stop-Service"
					bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.* > null"
					sleep(5)
					bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.war.* > null"
					echo "deploy the itrac.war"
					echo "copying new itrac web.war file web-${devrelease}.war"
					bat "copy web-${devrelease}.war \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\"

					// start service
					//bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Start-Service"
					sleep(5)
					println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "DEV Environment Deployed"
	} else {
		stage ('Deploy to DEV')
		echo "DEV Environment Not Deployed"
	}
} // end of dev

def QA() {
	echo "${env.userInput}"

	if(env.envInput == 'QA'){
		stage ('Deploy to QA')
		echo "Promoting to QA"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["SQITRweb186V"]
		String serviceName = "JBossEAP71"
		String qarelease = "null"
		String rollbackversion = "null"
		
		if(env.userInput == 'Rollback'){
			echo "There is NO ROLLBACK use promote"
			//cleanWs()
			//get rollback version

		} else {
			echo "ENTER PROMOTE"
			//cleanWs()
			//save the current version for rollback

			//get the version from dev
			echo "${pipelineData}\\dev\\devrelease.txt"
			String devtoqa = readFile "${pipelineData}\\dev\\devrelease.txt"
			echo "deploying version ${devtoqa} to QA"
			qarelease = devtoqa.trim();
			// wget the requested file from artifacoty 
			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${qarelease}/web-${qarelease}.war"
			writeFile file: "qarelease.txt", text: qarelease
			echo "writefile ${qarelease}"
			bat "copy /Y qarelease.txt ${pipelineData}\\qa"
		}

		echo "${serviceName}" + "service"
		echo "${qarelease}" + "qa release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]

			echo "deleteing itrac.war file"
			bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.* > null"
			sleep(5)
			bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.war.* > null"
			
			echo "copying new itrac web.war file web-${qarelease}.war"
			bat "copy web-${qarelease}.war \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\"
			sleep(5)

			//stop jboss
			//echo "Stop JBossEAP71"
			//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Stop-Service"'
			//sleep(10)
			//echo "start JBossEAP71"
			//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Start-Service"'
			//give it time to chill out
			//sleep(15)
			//update qarelease
			sleep(5)
			println "inside loop"
			
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "QA Environment Deployed"
	} else {
		stage ('Deploy to QA')
		echo "QA Environment Not Deployed"
	}
} //end of QA

//##################################################################################

def UAT() {
	echo "${env.userInput}"
	
	if(env.envInput == 'UAT'){
		stage ('Deploy to UAT')
		echo "Promoting to UAT"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["SUITRweb185V"]
		String serviceName = "JBossEAP7.1"
		String uatrelease = "null"
		String rollbackversion = "null"
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			cleanWs()
			//get and save version for a rollback
			uatrelease = readFile "${pipelineData}\\uat\\rollback.txt"
			echo "uat rollbackversion" + "" + "$uatrelease"
			writeFile file: "uatrelease.txt", text: uatrelease
			// wget the file from Artifactory
			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${uatrelease}/web-${uatrelease}.war"
			writeFile file: "uatrelease.txt", text: uatrelease
			bat "copy /Y uatrelease.txt ${pipelineData}\\uat"
		} else {
			echo "ENTER PROMOTE"
			cleanWs()
			//save the current version for rollback
			rollbackversion = readFile "${pipelineData}\\uat\\uatrelease.txt"
			writeFile file: "rollback.txt", text: rollbackversion
			bat "copy rollback.txt ${pipelineData}\\uat"
			//get the version from qa
			String qatouat = readFile "${pipelineData}\\qa\\qarelease.txt"
			echo "deploying version ${qatouat} to UAT"
			uatrelease = qatouat.trim();
			// wget the requested file from artifacoty 
			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${uatrelease}/web-${uatrelease}.war"
			// save off the release
			writeFile file: "uatrelease.txt", text: uatrelease
			bat "copy /Y uatrelease.txt ${pipelineData}\\uat"
		}
		

		echo "${serviceName}" + "service"
		echo "${uatrelease}" + "uat release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]

				echo "${serviceName}" + "${uatrelease}"
				echo "deleteing itrac.war file"
				bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.* > null"
				sleep(5)
				bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\web*.war.* > null"
				
				echo "copying new itrac web.war file web-${uatrelease}.war"
				bat "copy web-${uatrelease}.war \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.1\\standalone\\deployments\\"
				sleep(5)

				//stop jboss
				//echo "Stop JBossEAP71"
				//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Stop-Service"'
				//sleep(10)
				//echo "start JBossEAP71"
				//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Start-Service"'
				//give it time to chill out
				//sleep(15)
				//update uatrelease
				sleep(5)
					println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "UAT Environment Deployed"
	} else {
		stage ('Deploy to UAT')
		echo "UAT Environment Not Deployed"
	}
} //end of UAT

//##################################################################################

def STG() {
	echo "${env.userInput}"
	
	if(env.envInput == 'STG'){
		stage ('Deploy to STG')
		echo "Promoting to STG"
		
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["Ssitrweb216V"]
		String serviceName = "JBossEAP71"
		String stgrelease = "null"
		String rollbackversion = "null"
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			cleanWs()
			//get and save version for a rollback
			stgrelease = readFile '${pipelineData}\\stg\\rollback.txt'
			echo "uat rollbackversion" + "" + "$stgrelease"
			writeFile file: "stgrelease.txt", text: stgrelease
			// wget the file from Artifactory

			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${stgrelease}/web-${stgrelease}.war"
			writeFile file: "stgrelease.txt", text: stgrelease
			bat "copy /Y stgrelease.txt ${pipelineData}\\stg"
		} else {
			echo "ENTER PROMOTE"
			cleanWs()
			//save the current version for rollback
			rollbackversion = readFile '${pipelineData}\\stg\\stgrelease.txt'
			writeFile file: "rollback.txt", text: rollbackversion
			bat "copy rollback.txt ${pipelineData}\\stg"
			//get the version from UAT
			String uattostg = readFile '${pipelineData}\\uat\\uatrelease.txt'
			echo "deploying version ${uattostg} to stg"
			stgrelease = uattostg.trim();
			// wget the requested file from artifacoty 
			bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-cert http://maven.incomm.com/artifactory/incomm-release/com/incomm/itrac/web/${stgrelease}/web-${stgrelease}.war"
			// save off the release
			writeFile file: "stgrelease.txt", text: stgrelease
			bat "copy /Y stgrelease.txt ${pipelineData}\\stg"
		}
		

		echo "${serviceName}" + "service"
		echo "${stgrelease}" + "STG release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]

				echo "${serviceName}" + "${stgrelease}"
				echo "deleteing itrac.war file"
				bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.0\\standalone\\deployments\\web*.* > null"
				sleep(5)
				bat "del /F \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.0\\standalone\\deployments\\web*.war.* > null"
				
				echo "copying new itrac web.war file web-${stgrelease}.war"
				bat "copy web-${stgrelease}.war \\\\${HOSTNAME[i]}\\D\$\\Applications\\JBoss\\jboss-eap-7.0\\standalone\\deployments\\"
				sleep(5)

				//stop jboss
				//echo "Stop JBossEAP71"
				//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Stop-Service"'
				//sleep(10)
				//echo "start JBossEAP71"
				//bat 'powershell "Get-Service JBossEAP71 -ComputerName ${HOSTNAME[i]} | Start-Service"'
				//give it time to chill out
				//sleep(15)
				//update stgrelease
					sleep(5)
					println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "STG Environment Deployed"
	} else {
		stage ('Deploy to STG')
		echo "STG Environment Not Deployed"
	}
} //end of STG

def DEVTesting(){
		stage('Dev smoke Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "DEV Smoke Testing"

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
			echo "Promote Sequence Smoke Testing"
			}
		} //end dev testing
		
}//end testing

def QATesting(){
		stage('QA Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "QA Smoke and Regression Testing"

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
			echo "Promote Sequence Smoke Testing"
			}
		} //end dev testing
		
}//end testing

def UATTesting(){
		stage('UAT Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "UAT Smoke Testing"

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
			echo "Promote Sequence Smoke Testing"
			}
		} //end dev testing
		
}//end testing

def STGTesting(){
		stage('STG Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "STG Smoke Testing"

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
			echo "Promote Sequence Smoke Testing"
			}
		} //end dev testing
		
}//end testing