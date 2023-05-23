import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import java.text.SimpleDateFormat
import java.lang.*

//#################
// SingleLoadServices
// QA servers are (sqiupapp01v) - 10.42.81.101, (sqiupapp02v)- 10.42.81.102 
//#################

node('linux1') {
	
    def serviceName="singleLoadService"
    def pipeline_id="${env.BUILD_TAG}"
    def srcArtifactLocation="${env.WORKSPACE}"
    def artifactDeploymentLoc="/opt/iup/singleloadservice/"
	def svnrevision="null"
	def folder="null"
	String devrelease = "null"
	def maven="/opt/apache-maven-3.2.1/bin/mvn"
	
jdk = tool name: 'openjdk-11.0.7.10-0'
	env.JAVA_HOME = "${jdk}"
	echo "jdk installation path is: ${jdk}"
	sh "${jdk}/bin/java -version"
	
	stage('input'){
		try{
			timeout(time: 20, unit: 'SECONDS') {
				env.userInput = input message: 'User input required', ok: 'Continue',
					parameters: [choice(name: 'Build dev or Promote?', choices: 'Build\nPromote\nRollback', defaultValue: Build, description: 'Build or Promote?')]
			}
			echo "${env.userInput}"
			echo "selection"
		} catch(err) {
			echo "catch but let it go"
			env.userInput="Build"
			echo "${env.userInput}"
		} 
	} //input


	try {

		stage('Build Preparation') {
		if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			cleanWs()

			checkout([$class: 'GitSCM', branches: [[name: '*/qa']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'scm-incomm', url: 'https://github.com/InComm-Software-Development/FSWEB-SingleLoadService.git']]])
			

				echo "GIT BRANCH"
				sh "/bin/git branch"

				
			}else {
			echo "Promote Sequence"
		}
			
		} //end build preperation
		sleep(5)
	
		stage('Compile and SonarQube Analysis'){
		if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
		      withSonarQubeEnv('sonarqube.incomm.com'){
				echo "Building and Sonar analysis"
				sh "${maven} clean deploy -f pom.xml -U -B -DskipTests sonar:sonar"
					
					echo "read the pom for dev since we have no dev"
					pom = readMavenPom file: 'pom.xml'
					devrelease = pom.getVersion();
					echo "${devrelease}" + "pomfile"
					writeFile file: "devrelease", text: devrelease
					echo "writefile ${devrelease}"
					sh "/bin/cp -f devrelease /app/pipeline-data/singleloadservice/dev"
			  }
		}else {
			echo "Promote Sequence"
		}				
		
		} //end Sonarqube
						
      stage("Quality Gate"){
//          timeout(time: 5, unit: 'MINUTES') {
//              def qg = waitForQualityGate()
//              if (qg.status != 'OK') {
//                  error "Pipeline aborted due to quality gate failure: ${qg.status}"
//             }
//         }
	echo "commented QG"
      }
		

	stage('Enter Environment'){
	if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
		
		//default environment
		env.envInput = "QA"

		echo "${env.userInput}"
		echo "${env.envInput}"
		
	} else {

		// select env for promote
		timeout(time: 20, unit: 'SECONDS') {
			env.envInput = input message: 'User input required', ok: 'Continue',
				 parameters: [choice(name: 'Select Environment', choices: 'QA\nSTG\nUAT\nINT\nLOD', description: 'Promoting to ?')]
			}

			echo "${env.envInput}"
		
		}
		
		echo ("Env: "+env.envInput)

//	 //	DEV()
//	//	DEVTesting()
		QA()
//	//	QATesting()
//	//	UAT()
//	//	UATTesting()
//	//	STG()
//	//	STGTesting()
//	//	INT()
//	//	INTTesting()
//	//	LOD()
//	//	LODTesting()
	} //end environment stage
		
		
		currentBuild.result = 'SUCCESS'

	} catch (any) {
	
        currentBuild.result = 'FAILURE'
		
    } finally {
	
        println 'Pipeline job: ' + pipeline_id + ' is a ' + currentBuild.result
		
        step([$class: "Mailer", notifyEveryUnstableBuild: true, recipients: "ppiermarini@incomm.com", sendToIndividuals: true])
	mail bcc: "", body: "STATUS: ${currentBuild.result}\n\nAction: ${env.userInput}\nEnv: ${env.envInput}\n\nJOB Name: ${env.JOB_BASE_NAME}\nJob Tag: ${env.BUILD_TAG} \n\nTo view the results Check console output:\n ${env.BUILD_URL}", cc: "", from: "pipeline@incomm.com", replyTo: "", subject: "${env.userInput} ${env.JOB_BASE_NAME} - Build # ${env.BUILD_TAG} to ${env.envInput} environment:  ${currentBuild.result}", to: "FS-Web@incomm.com ppiermarini@incomm.com dstovall@incomm.com"
	}
echo "${env.envInput}"

}//node

//##############################################################################


def DEV() {
	echo "${env.userInput}"

	if(env.envInput == 'DEV'){
		stage ('Deploy to DEV')
		echo "Promoting to DEV"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]

		
		String[] HOSTNAME = ["SDS00MDMAPP01V", "SDS00MDMAPP02V"]
		String serviceName = "null"
		String devrelease = "null"
		String rollbackversion = "null"
		String artifactDeploymentLoc = "x"
		String DeployLocation = " "

			echo "DEPLOYING TO DEV"

			//save the current version for rollback
			rollbackversion = readFile '/app/pipeline-data/mdm_api/dev/devrelease'
			writeFile file: "devrollback", text: rollbackversion
			sh "/bin/cp -f devrollback /app/pipeline-data/mdm_api/dev"
			//get the version
			echo "read the pom"
			pom = readMavenPom file: 'pom.xml'
			devrelease = pom.getVersion();
			echo "${devrelease}" + "pomfile"

			// wget the requested file from artifacoty 
			sh "/bin/wget --no-check-certificate https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-api-service-ws/${devrelease}/mdm-api-service-ws-${devrelease}.war"
			
			echo "making some subfolders to put the files..."
			sh "/bin/mkdir common && /bin/mkdir fraud && /bin/mkdir qts_dev && /bin/mkdir bin/"
				
				echo "svn export the configuration files..."
				sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/common/log4j2-mdm-ws.xml common/"
				sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/archive/mdm-fraud/dev/fraud-api.properties fraud/"
				//sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/tc-server/mdm-ws-instance/qts_dev/bin/setenv.sh qts_dev/"
				sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_dev/mdm-alfresco-client.properties qts_dev/"
				sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_dev/mdm-api.properties qts_dev/"
				sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_dev/mdm-external-processing.properties qts_dev/"
			
			
			
			writeFile file: "devrelease", text: devrelease
			echo "writefile ${devrelease}"
			sh "/bin/cp -f devrelease /app/pipeline-data/mdm_api/dev"
		

		echo "${serviceName}" + "service"
		echo "${devrelease}" + "dev release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "TOTO" + "p${HOSTNAME[i]}p"
				echo "${serviceName}" + "${devrelease}"
				// stop service
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance stop"""
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance status"""
				sleep(5)
			
				echo "copying the war file..."
				sh "scp -i ~/.ssh/pipeline -o StrictHostKeyChecking=no mdm-api-service-ws-${devrelease}.war root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps/mdm-api-ws-2.war"
				
				echo "copying the libs..."
				sh "scp -i ~/.ssh/pipeline -rq qts_dev/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				sh "scp -i ~/.ssh/pipeline -rq common/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				sh "scp -i ~/.ssh/pipeline -rq fraud/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				
				
				//sh "scp -i ~/.ssh/pipeline -rq bin/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/"

				echo "setting ownership..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/setenv.sh' """
				echo "setting permissions..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod -R 644 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps/mdm-api-ws-2.war' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 644 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/*' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/setenv.sh' """
			
				
				sleep(5)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance start"""
				sleep(3)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance status"""
				

					

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
//##############################################################
def QA() {

// QA servers are (sqiupapp01v) - 10.42.81.101, (sqiupapp02v)- 10.42.81.102 
	
	if(env.envInput == 'QA'){
		stage ('Deploy to QA')
		echo "Promoting to QA"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["10.42.81.101", "10.42.81.102"]
		String serviceName = " null"
		String qarelease = "null"
		String rollbackversion = "null"
		String remote_md5sum = "null"

		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			//cleanWs()
			//get rollback version
			qarelease = readFile '/app/pipeline-data/singleloadservice/qa/qarollback'
			echo "qa rollbackversion" + "" + "$qarelease"
			writeFile file: "qarelease", text: qarelease
			// wget the file from Artifactory
		//	sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/sls/singleloadservice/${qarelease}/singleload-${qarelease}.jar"
			

		//	sh "/bin/cp -f qarelease /app/pipeline-data/singleloadservice/qa"
			
			} else {
			echo "ENTER PROMOTE"
			//cleanWs()
			//save the current version for rollback
			rollbackversion = readFile '/app/pipeline-data/singleloadservice/qa/qarelease'
			writeFile file: "qarollback", text: rollbackversion
			sh "cp -f qarollback /app/pipeline-data/singleloadservice/qa"
			//get the version from dev
			String devtoqa = readFile '/app/pipeline-data/singleloadservice/dev/devrelease'
			echo "deploying version ${devtoqa} to QA"
			qarelease = devtoqa.trim();
			
			// wget the requested file from artifacoty 
	sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/sls/singleloadservice/${qarelease}/singleloadservice-${qarelease}.jar"
			

			writeFile file: "qarelease", text: qarelease
			echo "writefile ${qarelease}"
			sh "cp -f qarelease /app/pipeline-data/singleloadservice/qa"
		}
		
		echo "${serviceName}" + "service"
		echo "${qarelease}" + "qa release"

		try {

			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]

				echo "${serviceName}" + "${qarelease}"
				// stop service
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service singleloadservice stop &>/dev/null"""
				//sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service singleloadservice status &>/dev/null"""
				sleep(5)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /bin/cp /opt/iup/singleloadservice/singleloadservice.jar /opt/iup/singleloadservice/archive/singleloadservice.jar_`date +%s%m%d%Y`"""
				
				sh "scp -i ~/.ssh/pipeline singleloadservice-${qarelease}.jar root@${HOSTNAME[i]}:/opt/iup/singleloadservice/singleloadservice.jar"
				sh """ssh -i ~/.ssh/pipeline root@${HOSTNAME[i]} '/bin/chmod 755 /opt/iup/singleloadservice/singleloadservice.jar'"""
				sleep(5)


			echo "MD5 Checksum"
			sh """/bin/md5sum singleloadservice-${qarelease}.jar | awk \'{print \$1}\'> localwarfile_checksum"""
			echo "remote MD5"
			sh """ssh -i ~/.ssh/pipeline root@${HOSTNAME[i]} /usr/bin/md5sum /opt/iup/singleloadservice/singleloadservice.jar > remote_md5sum """
			echo "mote ${remote_md5sum}"
			def md5local = readFile('localwarfile_checksum').trim()
			def md5remote = readFile('remote_md5sum').trim()
			String[] target_md5sum = md5remote.split(" ", 2);
			//echo "${target_md5sum[0]}"
			echo "local  ${md5local}"
			echo "remote ${target_md5sum[0]}"
			sleep(2)
			if ( "${md5local}" == "${target_md5sum[0]}" ){
			    echo " war file is confirmed"
			} else {
			    echo "war file could be corrupt"
				exit 1
			} 
			

				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service singleloadservice start &>/dev/null'"""		
				sleep(3)
				//sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/sbin/service singleloadservice status &>/dev/null' """

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
		String[] HOSTNAME = ["AQSPL02APL2V"]
		String serviceName = "JBOSSSEJSPLAPLX64"
		String uatrelease = "null"
		String rollbackversion = "null"
		String DeployLocation = "D\$\\incomm\\jboss\\deploy\\projects"
		
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			cleanWs()
			//get and save version for a rollback
			uatrelease = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat\\rollback.txt'
			echo "uat rollbackversion" + "" + "$uatrelease"
			writeFile file: "uatrelease.txt", text: uatrelease
			// wget the file from Artifactory
		//	bat "wget.exe --no-check-cert --user=ppiermarini --password=AP7yX7MCtvPThPkay6LEFiSGeKRmVHjAYwJAMi http://maven.incomm.com/artifactory/incomm-release/com/incomm/spl/project/apple/ear/spl-appleRest/${uatrelease}/spl-appleRest-${uatrelease}.ear"
			writeFile file: "uatrelease.txt", text: uatrelease
		//	bat "copy /Y uatrelease.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat"
		} else {
			echo "ENTER PROMOTE"
			//save the current version for rollback
			rollbackversion = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat\\uatrelease.txt'
			writeFile file: "rollback.txt", text: rollbackversion
		//	bat "copy rollback.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat"
			//get the version from qa
			String qatouat = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\qa\\qarelease.txt'
			echo "deploying version ${qatouat} to UAT"
			uatrelease = qatouat.trim();
			// wget the requested file from artifacoty 
		//	bat "wget.exe --no-check-cert --user=ppiermarini --password=AP7yX7MCtvPThPkay6LEFiSGeKRmVHjAYwJAMi http://maven.incomm.com/artifactory/incomm-release/com/incomm/spl/project/apple/ear/spl-appleRest/${uatrelease}/spl-appleRest-${uatrelease}.ear"
			// save off the release
			writeFile file: "uatrelease.txt", text: uatrelease
		//	bat "copy /Y uatrelease.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat"
		}
		

		echo "${serviceName}" + "service"
		echo "${uatrelease}" + "uat release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "TOTO" + "p${HOSTNAME[i]}p"
				echo "${serviceName}" + "${uatrelease}"
				echo "inside loop"
					// stop service
			//		bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Stop-Service"
					sleep(4)
					//bat "move \\\\${HOSTNAME[i]}\\D\$\\incomm\\jboss\\deploy\\projects\\appleRestapi-servlet-${rollbackversion}.ear \\\\${HOSTNAME[i]}\\D\$\\incomm\\tmp"
					echo "deploy the new spl-appleRest-${uatrelease}.ear"
					echo "copy /Y \"${env.WORKSPACE}\\spl-appleRest-${uatrelease}.ear\" \\\\${HOSTNAME[i]}\\${DeployLocation}"
			//		bat "copy /Y \"${env.WORKSPACE}\\spl-appleRest-${uatrelease}.ear\" \\\\${HOSTNAME[i]}\\${DeployLocation}"
					// start service
			//		bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Start-Service"
					sleep(5)
			}
			echo "outside loop"
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
		String[] HOSTNAME = ["AQSPL02APL2V"]
		String serviceName = "JBOSSSEJSPLAPLX64"
		String stgrelease = "null"
		String rollbackversion = "null"
		String DeployLocation = "D\$\\incomm\\jboss\\deploy\\projects"
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			cleanWs()
			//get and save version for a rollback
			stgrelease = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\stg\\rollback.txt'
			echo "uat rollbackversion" + "" + "$stgrelease"
			writeFile file: "stgrelease.txt", text: stgrelease
			// wget the file from Artifactory
		//	bat "wget.exe --no-check-cert --user=ppiermarini --password=AP7yX7MCtvPThPkay6LEFiSGeKRmVHjAYwJAMi http://maven.incomm.com/artifactory/incomm-release/com/incomm/spl/project/apple/ear/spl-appleRest/${stgrelease}/spl-appleRest-${stgrelease}.ear"
			writeFile file: "stgrelease.txt", text: stgrelease
			//bat "copy /Y stgrelease.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\stg"
		} else {
			echo "ENTER PROMOTE"
			cleanWs()
			//save the current version for rollback
			rollbackversion = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\stg\\stgrelease.txt'
			writeFile file: "rollback.txt", text: rollbackversion
		//	bat "copy rollback.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\stg"
			//get the version from UAT
			String uattostg = readFile '\\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\uat\\uatrelease.txt'
			echo "deploying version ${uattostg} to stg"
			stgrelease = uattostg.trim();
			// wget the requested file from artifacoty 
		//	bat "wget.exe --no-check-cert --user=ppiermarini --password=AP7yX7MCtvPThPkay6LEFiSGeKRmVHjAYwJAMi  http://maven.incomm.com/artifactory/incomm-release/com/incomm/spl/project/apple/ear/spl-appleRest/${stgrelease}/spl-appleRest-${stgrelease}.ear"
			// save off the release
			writeFile file: "stgrelease.txt", text: stgrelease
		//	bat "copy /Y stgrelease.txt \\\\atlincfps01\\Engineering\\scm\\pipeline_data\\appleRestapi\\stg"
		}
		

		echo "${serviceName}" + "service"
		echo "${stgrelease}" + "STG release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "TOTO" + "p${HOSTNAME[i]}p"
				echo "${serviceName}" + "${stgrelease}"
					// stop service
					//bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Stop-Service"
					sleep(5)
					echo "deploy the new spl-appleRest-${stgrelease}.ear"
					echo "copy /Y \"${env.WORKSPACE}\\spl-appleRest-${stgrelease}.ear\" \\\\${HOSTNAME[i]}\\${DeployLocation}"
				//	bat "copy /Y \"${env.WORKSPACE}\\spl-appleRest-${stgrelease}.ear\" \\\\${HOSTNAME[i]}\\${DeployLocation}"
					// start service
				//	bat "powershell Get-Service ${serviceName} -ComputerName ${HOSTNAME[i]} ^| Start-Service"
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

//#############################################################################

def INT() {
	echo "${env.userInput}"
	
	if(env.envInput == 'INT'){
		stage ('Deploy to INT')
		echo "Promoting to INT"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["stmdmapp01v0771v", "stmdmapp02v0772v"]
		String serviceName = "mdm-ws-instance"
		String intrelease = "null"
		String rollbackversion = "null"
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			//cleanWs()
			//get rollback version
			intrelease = readFile '/app/pipeline-data/mdm_api/int/introllback'
			echo "qa rollbackversion" + "" + "$qarelease"
			writeFile file: "intrelease", text: intrelease
			// wget the file from Artifactory
			sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-api-service-ws/${intrelease}/mdm-api-service-ws-${intrelease}.war" 
			
			echo "making some subfolders to put the files..."
			sh "/bin/mkdir common fraud qts_int bin/"	
			echo "svn export the configuration files..."
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/common/log4j2-mdm-ws.xml common/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/archive/mdm-fraud/int/fraud-api.properties fraud/"
			//sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/tc-server/mdm-ws-instance/qts_int/bin/setenv.sh bin/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_int/mdm-alfresco-client.properties int/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_int/mdm-api.properties int/"
			
			sh "/bin/cp -f intrelease /app/pipeline-data/mdm_api/int"
			
			} else {
			echo "ENTER PROMOTE"
			cleanWs()
			//save the current version for rollback
			rollbackversion = readFile '/app/pipeline-data/mdm_api/int/intrelease'
			writeFile file: "introllback", text: rollbackversion
			sh "cp -f introllback /app/pipeline-data/mdm_api/int"
			//get the version from qa
			String qatoint = readFile '/app/pipeline-data/mdm_api/int/intrelease'
			echo "deploying version ${qatoint} to INT"
			intrelease = qatoint.trim();
			
			// wget the requested file from artifacoty 
			sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-api-service-ws/${intrelease}/mdm-api-service-ws-${intrelease}.war"
			
			echo "making some subfolders to put the files..."
			sh "/bin/mkdir common fraud qts_int bin/"	
			echo "svn export the configuration files..."
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/common/log4j2-mdm-ws.xml common/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/archive/mdm-fraud/int/fraud-api.properties fraud/"
			//sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/tc-server/mdm-ws-instance/qts_int/bin/setenv.sh bin/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_int/mdm-alfresco-client.properties qts_int/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/qts_int/mdm-api.properties qts_int/"
			
			writeFile file: "intrelease", text: intrelease
			echo "writefile ${intrelease}"
			sh "cp -f intrelease /app/pipeline-data/mdm_api/int"
		}
		
		echo "${serviceName}" + "service"
		echo "${intrelease}" + "int release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "TOTO" + "p${HOSTNAME[i]}p"
				echo "${serviceName}" + " ${intrelease}"
				echo "stopping the mdm-ws-instance...."
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance stop"""
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance status"""
				sleep(5)
				
				echo "copying the mdm-api-service-ws-${intrelease}.war file..."
				sh "scp -i ~/.ssh/pipeline -o StrictHostKeyChecking=no mdm-api-service-ws-${intrelease}.war root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps/mdm-api-ws-2.war"
				
				echo "copying the libs..."
				sh "scp -i ~/.ssh/pipeline -rq qts_int/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				sh "scp -i ~/.ssh/pipeline -rq common/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				sh "scp -i ~/.ssh/pipeline -rq fraud/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"
				
				
				//sh "scp -i ~/.ssh/pipeline -rq bin/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/"

				echo "setting ownership..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/setenv.sh' """
				echo "setting permissions..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod -R 644 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/webapps/mdm-api-ws-2.war' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 644 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/*' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/bin/setenv.sh' """
			
				sleep(5)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance start"""
				sleep(3)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service mdm-ws-instance status"""
				


				println "inside loop"
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "INT Environment Deployed"
	} else {
		stage ('Deploy to INT')
		echo "INT Environment Not Deployed"
	}
} //end of INT

//##############################################################################

def LOD() {
	echo "${env.userInput}"

	if(env.envInput == 'LOD'){
		stage ('Deploy to LOD')
		echo "Promoting to LOD"
		// example for multiple servers: String[] HOSTNAME = ["Daffy", "Donald", "Scrooge"]
		String[] HOSTNAME = ["slmdmapp01v", "slmdmapp02v", "slmdmapp03v", "slmdmapp04v", "slmdmapp05v", "slmdmapp06v"]
		String instanceName = "mdm-ws-instance"
		String lodrelease = "null"
		String rollbackversion = "null"
		
		if(env.userInput == 'Rollback'){
			echo "ENTER ROLLBACK"
			//cleanWs()
			//get rollback version
			qarelease = readFile '/app/pipeline-data/mdm_api/lod/lodrollback'
			echo "qa rollbackversion" + "" + "$lodrelease"
			writeFile file: "lodrelease", text: lodrelease
			// wget the file from Artifactory
			sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-api-service-ws/${lodrelease}/mdm-api-service-ws-${lodrelease}.war"
			
			echo "making some subfolders to put the files..."
			sh "/bin/mkdir common fraud lod bin/"	
			echo "svn export the configuration files..."
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-api.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-alfresco-client.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-external-processing.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/common/log4j2-mdm-ws.xml common/"
				
			sh "/bin/cp -f lodrelease /app/pipeline-data/mdm_api/lod"
			
			} else {
			echo "ENTER PROMOTE"
			cleanWs()
			//save the current version for rollback
			rollbackversion = readFile '/app/pipeline-data/mdm_api/lod/lodrelease'
			writeFile file: "lodrollback", text: rollbackversion
			sh "cp -f lodrollback /app/pipeline-data/mdm_api/lod"
			//get the version from int
			String inttolod = readFile '/app/pipeline-data/mdm_api/lod/lodrelease'
			echo "deploying version ${inttolod} to LOD"
			lodrelease = inttolod.trim();
			
			// wget the requested file from artifacoty 
			sh "/bin/wget --no-check-cert https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-api-service-ws/${lodrelease}/mdm-api-service-ws-${lodrelease}.war"
			echo "making some subfolders to put the files..."
			sh "/bin/mkdir common fraud lod bin/"	
			echo "svn export the configuration files..."
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-api.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-alfresco-client.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/lod/mdm-external-processing.properties lod/"
			sh "/bin/svn export https://svn.incomm.com/svn/devel/tp/mdm/configuration/mdm-api-config/common/log4j2-mdm-ws.xml common/"
			
			writeFile file: "lodrelease", text: lodrelease
			echo "writefile ${lodrelease}"
			sh "cp -f lodrelease /app/pipeline-data/mdm_api/lod"
		}
		
		echo "${instanceName}"
		echo "${lodrelease}" + "lod release"

		try {
		
			for (i = 0; i <HOSTNAME.size(); i++) {
				println HOSTNAME[i]
				echo "TOTO" + "p${HOSTNAME[i]}p"
				echo "${instanceName}" + "${lodrelease}"
				// stop service
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service ${instanceName} stop"""
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service ${instanceName} status"""
				sleep(5)
				
				
				echo "copying the war file..."
				sh "scp -i ~/.ssh/pipeline -o StrictHostKeyChecking=no mdm-api-service-ws-${lodrelease}.war root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/webapps/mdm-api-ws-2.war"
				sleep(2)
				echo "copying the libs..."
				sh "scp -i ~/.ssh/pipeline -rq lod/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib/"
				sh "scp -i ~/.ssh/pipeline -rq common/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib/"
				//sh "scp -i ~/.ssh/pipeline -rq fraud/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib/"
				
				//sh "scp -i ~/.ssh/pipeline -rq bin/* root@${HOSTNAME[i]}:/var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/bin/"

				echo "setting ownership..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/webapps' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chown -R tcserver:pivotal /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/bin/setenv.sh' """
				echo "setting permissions..."
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod -R 644 /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/webapps/mdm-api-ws-2.war' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}' """
				sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 644 /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/lib/*' """
				//sh """ ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} '/bin/chmod 755 /var/opt/pivotal/pivotal-tc-server-standard/${instanceName}/bin/setenv.sh' """
			
				sleep(5)

				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service ${instanceName} start"""
				sleep(3)
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME[i]} /sbin/service ${instanceName} status"""

				println "inside loop"
				
			}
			println "outside loop"
		}
		catch(exc){
				echo "caught exception"
					throw new AbortException("${job.fullDisplayName} aborted.")
		}

		echo "LOD Environment Deployed"
	} else {
		stage ('Deploy to LOD')
		echo "LOD Environment Not Deployed"
	}
} //end of LOD

//###################################################################################

def DEVTesting(){
		stage('Dev smoke Testing'){
			if((env.userInput != 'Promote') && (env.userInput != 'Rollback')){
			echo "DEV Smoke Testing"
			sleep(10)
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
			sleep(10)
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
			sleep(10)
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
			sleep(4)
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
