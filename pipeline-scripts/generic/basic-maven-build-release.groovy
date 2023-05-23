import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
buildType = "${BUILD_TYPE}"
gitRepository="${GIT_REPOSITORY}"
gitBranch="${BRANCH}"
snykTokenId = "${SNYK_TOKEN_ID}"
jdkVersion = "${JDK_VERSION}"
maven = "${MAVEN_LOCATION}"
flags = "${MAVEN_FLAGS}"
skipTests = "${SKIP_MAVEN_TESTS}"

// Globals
emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, jrivett@incomm.com"
gitCreds = "scm-incomm"
testFlag = ""

node('linux'){
    try {  
		cleanWs()

		stage('set-jdk') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}

        stage('check-for-testSkip') {
            if(skipTests == 'true') {
                testFlag = "-DskipTests"
            }
        }

        stage('checkout') {
			githubCheckout(gitCreds,gitRepository,gitBranch)
        }

        stage('build') {
            if(buildType == 'Build') {
                sh "${maven} clean deploy ${MAVEN_FLAGS} -f pom.xml ${testFlag}"
            }
            else {
                echo "NOT BUILDING FOR BUILD_TYPE=${buildType}"
            }
        }

        stage('release') {
            if(buildType == 'Release') {
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
            }
            else {
                echo "NOT RELEASING FOR BUILD_TYPE=${buildType}"
            }
        }

        stage('snyk-scan') {
            pom = readMavenPom file: 'pom.xml'
            artifactId = pom.getArtifactId()
            artifactVersion = pom.getVersion()
            snykScanV2(snykTokenId, artifactId, artifactVersion)
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
