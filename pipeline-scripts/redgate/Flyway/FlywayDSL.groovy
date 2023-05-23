import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// database repo
gitRepository="https://github.com/InComm-Software-Development/Database-Oracle.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

flywayCreds= "${creds}"
//Database URL 
dbUrl="jdbc:oracle:thin:@//sdmdmora01v.unx.incommtech.net:1521/PDEVMDM"

//Database SQL ocation 
scriptLocation="E:\\jenkins-home\\workspace\\redgate-pipeline\\mdm\\Tables"


maven="/opt/apache-maven-3.2.1/bin/mvn"
currentBuild.result="SUCCESS"


node('windows'){

flyway= tool name: 'flyway-6.5.3'
echo "flyway version= ${flyway}" 
echo " "
echo "flyway Credentials = ${flywayCreds}"
echo "user Input         = ${userInput}"
echo "git Branch         = ${gitBranch}"

	stage('Checkout') {

			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
			//checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'scm-incomm', url: 'https://github.com/InComm-Software-Development/Database-Oracle.git']]])

	}

	stage('Push to Artifactory') {
		
		Naming convention 
		Application in the
		
		foreach env
		jdbc connections per environment.
		server names

		Rollback script

		mdm dev qa Int prod jdbc strings?

		prod script on buildfs

		MDM QA:sqmdmora01v.unx.incommtech.net
		MDM INT:stmdmora01v.unx.incommtech.net
		MDM PROD:mdmdbalias.incomm.com
		DEV : jdbc:oracle:thin:@//sdmdmora01v.unx.incommtech.net:1521/PDEVMDM

		QA: jdbc:oracle:thin:@//sqmdmora01v.unx.incommtech.net:1521/PQA2MDM

		INT : jdbc:oracle:thin:@//stmdmora01v.unx.incommtech.net:1521/PINTMDM

		PROD: jdbc:oracle:thin:@//mdmdbalias.incomm.com:1521/PPRDMDM

		//if release 
		//select tag      
		//tar version
		//tar rollback
		//upload version
		//else	going to dev
		
		//dev stuff here

	}	

	stage('Apply SQL to database') {

			echo 'Flyway runner...'
			echo "creds ${flywayCreds}"
			// SCM: Need to debug this pipeline groovy
			//flywayrunner commandLineArgs: '-X', credentialsId: 'FLYWAY_TEST', flywayCommand: 'migrate', installationName: 'flyway-6.5.3', locations: 'filesystem:E:\\jenkins-home\\workspace\\redgate-pipeline', url: 'jdbc:oracle:thin:@//sdmdmora01v.unx.incommtech.net:1521/PDEVMDM'
			
			withCredentials([usernamePassword(credentialsId: "${flywayCreds}", passwordVariable: 'flyPass', usernameVariable: 'flyUser')]) {
			
			    bat """E:\\app\\jenkins-tools\\flyway-6.5.3\\flyway migrate -user="${flyUser}" -password="${flyPass}" -url="${dbUrl}" -locations=filesystem:"${scriptLocation}" """
			
			}

			// SCM: Need to debug the pipeline DSL
			//flywayRunner { 
				//			name('flyway-6.5.3')
				//			command('migrate')
				//			url('jdbc:mysql://mysqlserver:3306/mydb')
				//			locations('filesystem:$WORKSPACE/')
				//			credentialsId('FLYWAY_TEST')
				//}
	}

	
}