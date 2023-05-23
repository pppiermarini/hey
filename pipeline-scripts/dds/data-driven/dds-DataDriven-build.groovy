import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


// Jenkins vars
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${BRANCH}"
pipelineAction = "${PIPELINE_ACTION}"
pipeline_id = "${env.BUILD_TAG}"

repoId = null
groupId = null
artifactId = null
artExtension = null
artifactName = null
			
// Pipeline Globals
gitCreds = "scm-incomm"
testName = "myTest"
maven = "/opt/apache-maven-3.2.5/bin/mvn"
emailDistribution = "jrivett@incomm.com dstovall@incomm.com pchattaraj@incomm.com"
snykCreds = "Snyk_DDS"
snyk = "/usr/bin/snyk-linux"
snykHtml = "/usr/bin/snyk-to-html-linux"
scanCodeDir = ""
pomPath = "pom.xml"
reports = []
folderBuildName = null
// Data Driven Placeholders
projectProperties = null

currentBuild.result = 'SUCCESS'

node('linux2'){
	try { 
	
		cleanWs()
	
		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}

		// Reading data-driven pipeline values from YML file
    	stage('Read YAML file') {
            echo 'Reading dataDriven.yml file'
            projectProperties = readYaml (file: 'digitaldeliveryDataDriven.yml')
            if (projectProperties == null) {
                throw new Exception("dataDriven.yml not found in the project files.")
            }

            // TODO: Ensure the values in this sanity check match the values in YML
            echo "Sanity Check"
            if (projectProperties.artifactInfo == null || projectProperties.deployment == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
			
			repoId = projectProperties.artifactInfo.repoId
            groupId = projectProperties.artifactInfo.groupId 
            artifactId = projectProperties.artifactInfo.artifactId 
            artExtension = projectProperties.artifactInfo.artExtension 
			artifactName = projectProperties.artifactInfo.artifactName
        }

        stage('Snyk Scans') {
			
			findPomVersion(folderBuildName)
			snykScan(snyk, snykHtml, scanCodeDir, folderBuildName, snykCreds, "${projectProperties.artifactInfo.artifactId}", labelScan)

			// publish all reports
			reports.each {
				echo "scanCodeDir: ${scanCodeDir}"
				publishSnykReport(scanCodeDir, it, it)
			}
			
        } //snyk scan
		
		stage('Build'){

			if (pipelineAction == 'build'){
                // is this build command okay?
				sh "${maven} clean deploy -f ${pomPath} -DskipTests -X"
			}

            else if (pipelineAction == 'release'){
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: "${pomPath}"
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"
			}
            
            else {
				echo "no build when PIPELINE_ACTION is: ${pipelineAction}"	
			}

		} //stage
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


def findPomVersion(folderBuildName) {
	if (folderBuildName == null) {
		getPomVersion()
    }

    else {
    dir("${folderBuildName}") {
    	getPomVersion()
    }
  }
}


def getPomVersion(){
	pom = readMavenPom file: 'pom.xml'
	labelScan = pom.getVersion();

	echo "Pom Version: ${labelScan}"
}

def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def tests(target, envName, testName){
	
	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
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