import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

projectProperties = null

// inputs from build with parameters
gitRepository="${SOURCE_REPO}"
gitBranch="${SOURCE_BRANCH}"
gitCreds="scm-incomm"


userInput="${PIPELINE_ACTION}"

//General pipeline
emailDistribution="rkale@incomm.com ppiermarini@incomm.com ssanka@incomm.com dkumar@incomm.com schennamsetty@incomm.com"


//Application array of pom and yml locations, they must be present in the same location of source control
//If pom is in root the value is null or else the folder name provided by FSAPI. 
applications = [
'apisecurity': [null],
'ccaget': ['CCA_Get'],
'ccaaction': ['CCA_Action'],
'b2b': [null],
'ingordc': [null]
]

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

// Folder Name if exists for pom & datadriven build locations
folderBuildName = applications[${appID}]

snyk = "/usr/bin/snyk-linux"
snykHtml = "/usr/bin/snyk-to-html-linux"
scanCodeDir = ""
labelScan = ""
reports = []

node('linux2'){
	try { 

        cleanWs()

        stage('Set Java Version') {
            jdk = tool name:'openjdk-11.0.5.10'
            env.JAVA_HOME = "${jdk}"
            echo "jdk installation path is: ${jdk}"
            sh "${jdk}/bin/java -version"
        }

		stage('checkout'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

        stage('Read YAML file') {
			echo "${folderBuildName}"
            findYaml(folderBuildName)
        }
         //@TODO: App sec yet to provide a token
        /*
        stage('Snyk Scans') {
            // Authenticate snyk

            scanCodeDir = "${env.WORKSPACE}/reports/"
			findPomVersion(folderBuildName)
			echo "Snyk auth for FSAPI"
			withCredentials([string(credentialsId: '', variable: 'AUTH')]) {
				sh "${snyk} auth ${AUTH}"
			}

            // initiate scan
			sh "${snyk} code test --json | ${snykHtml} -o ${projectProperties.buildInfo.artifactId}-${labelScan}-code.html"
			sh "${snyk} test --all-projects --json | ${snykHtml} -o ${projectProperties.buildInfo.artifactId}-${labelScan}-third-party.html"
			
			// add reports to array
			reports.add("${projectProperties.buildInfo.artifactId}-${labelScan}-code.html")
			reports.add("${projectProperties.buildInfo.artifactId}-${labelScan}-third-party.html")
			
			// move code reports to scanCodeDir
			sh """ [ -d ${scanCodeDir} ] || mkdir ${scanCodeDir} """
			reports.each {
				sh """
					mv ${env.WORKSPACE}/${it} ${scanCodeDir}
				"""
			}

			// publish all reports
			reports.each {
				echo "scanCodeDir: ${scanCodeDir}"
				publishSnykReport(scanCodeDir, it, it)
			}
			
        }*/

        stage('Build-Release') {
            findPom(folderBuildName)
            
        }
		
		
	}     
    
    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}
	

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def findPom(folderBuildName)  {
if (folderBuildName == null) {
    buildRelease(userInput)
}

else {
    dir("${folderBuildName}") {
     buildRelease(userInput)
    }
}

}

def findYaml(folderBuildName) {
    if (folderBuildName == null) {
     getYaml()
    }

    else {
    dir("${folderBuildName}") {
     getYaml()
    }
}

}

def findPomVersion(folderBuildName) {
	if (folderBuildName == null) {
		getPomVersion
    }

    else {
    dir("${folderBuildName}") {
    	getPomVersion
    }
  }
}

def getYaml() {
     echo 'Reading fsapiDataDriven.yml file'
    projectProperties = readYaml (file: 'fsapiDataDriven.yml')
    if (projectProperties == null) {
                throw new Exception("fsapiDataDriven.yml not found in the project files.")
    }
	if (projectProperties.emailNotification != null) {
		emailDistribution = projectProperties.emailNotification
	}
	/*
            projectProperties.buildInfo.repoId == null || projectProperties.buildInfo.groupId == null ||
			projectProperties.deployInfo.common == null || projectProperties.deployInfo.commonLoc == null || projectProperties.deployInfo.artExtension == null || 
			projectProperties.deployInfo.artifactName == null || projectProperties.deployInfo.artifactDeploymentLoc == null || projectProperties.deployInfo.propertiesDeploymentLoc == null ||
			projectProperties.deployInfo.srcProperties == null || projectProperties.deployInfo.propArchive == null || projectProperties.deployInfo.serviceName == null ||
			projectProperties.deployInfo.user == null || projectProperties.deployInfo.group == null -> Add for CD template
	*/
    echo "Sanity Check"
            if (projectProperties.buildInfo.artifactId == null ) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        
}

def getPomVersion(){
	pom = readMavenPom file: 'pom.xml'
	labelScan = pom.getVersion();

	echo "Pom Version: ${labelScan}"
}

def buildRelease(userInput) {
	if (userInput == 'Build'){
		sh "${maven} clean deploy -f pom.xml -DskipTests"	
       		
	} else if (userInput == 'Release'){
		echo "Maven Release Build"
		echo "Maven Release:Prepare..."
		pom = readMavenPom file: 'pom.xml'
		def mavenReleaseVersion = pom.getVersion();
		echo " maven release= " + "${mavenReleaseVersion}"
		sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
		sleep(3)
		sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
	    sleep(4)
		def str = mavenReleaseVersion.split('-');
		def myrelease = str[0];
		writeFile file: "mavenrelease", text: myrelease
		}
        else {
		echo "no build"	
	}
}

def publishSnykReport(dir, filename, title){
	echo "dir: ${dir}"
	publishHTML (target : [allowMissing: false,
	alwaysLinkToLastBuild: true,
	keepAll: true,
	reportDir: "${dir}",
	reportFiles: "${filename}",
	reportName: "Report for ${title}",
	reportTitles: "The Report of ${title}"])
}
///// The End
