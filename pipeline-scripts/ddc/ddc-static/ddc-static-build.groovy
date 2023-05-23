import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
gitBranch = "${GIT_BRANCH}"

// Globals
emailDistribution = "jrivett@incomm.com"
gitCreds = 'scm-incomm'
gitRepository = 'https://github.com/InComm-Software-Development/ddc-static.git' 
maven = "/opt/apache-maven-3.2.1/bin/mvn"
artifactoryURL = "https://maven.incomm.com"
artifactoryPath = "artifactory/scm/com/incomm/ddc/ddc-static"
artifactId = "ddc-static"

def	pom;
def	pomVersion;
def	pomGroupId;
def groupIdDir;

node('linux'){
    try {  
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
        }

        stage('build-sass') {
            // This goal compiles the SASS files in repository to CSS files and put them in 'target' dir
            sh "${maven} compile -e"

            // Move HTML files to target dir
            sh "cp -R static/html target/html"
        }

        stage('create-tar'){
            pom = readMavenPom file: 'pom.xml'
            pomVersion = pom.getVersion();
            pomGroupId = pom.getGroupId();
            groupIdDir = pomGroupId.replaceAll("\\.", "/")

            // This mimics the deployment structure and zips those files into a tar file
            sh """
                cp -R ${env.WORKSPACE}/static/assets ${env.WORKSPACE}/target/assets
                mv ${env.WORKSPACE}/target/merchants ${env.WORKSPACE}/target/assets
                cp -R ${env.WORKSPACE}/target/css/* ${env.WORKSPACE}/target/assets
                cd ${env.WORKSPACE}/target && tar -cf ${artifactId}-${pomVersion}.tar html/ assets/
                cp -R ${env.WORKSPACE}/target/${artifactId}-${pomVersion}.tar ${env.WORKSPACE}/${artifactId}-${pomVersion}.tar
            """
        }

        stage('upload-to-artifactory') {
            if(pomVersion.contains('SNAPSHOT')){
                sh """
                    /usr/bin/jfrog rt u ${artifactId}-${pomVersion}.tar incomm-snapshot/${groupIdDir}/${artifactId}/${pomVersion}/
                """
            }
            else {
                sh """
                    /usr/bin/jfrog rt u ${artifactId}-${pomVersion}.tar incomm-release/${groupIdDir}/${artifactId}/${pomVersion}/
                """
            }
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
}
