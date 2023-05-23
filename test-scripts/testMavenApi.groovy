import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _


//globals

currentBuild.result = "SUCCESS"
emailDistribution="rkale@incomm.com "

user = "${myUser}"
pass = "${pass}"
groupID="hello-world-testing"

artifactID="hello-world"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))


/* Documentation link: https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API
* sh """curl -u myUser:myPass -X PUT "http://localhost:8081/artifactory/my-repository/my/new/artifact/directory/file.txt" -T Desktop/myNewFile.txt""" --> POST to the location in artifactory
* sh """curl -u myUser:myPass https://maven.incomm.com/artifactory/api/search/gavc?g=${groupID}&a=${artifactid}""" --> GET everything with regarding to the ArtifactID & GroupID
*/
/*
'''
  {
"results": [
    {
        "uri": "http://localhost:8081/artifactory/api/incomm-snapshot/hello-world-testing/hello-world/0.0.1-SNAPSHOT/hello-world-0.0.1-20180822.130553-1.jar"
    },{
        "uri": "http://localhost:8081/artifactory/api/incomm-snapshot/hello-world-testing/hello-world/0.0.2-SNAPSHOT/hello-world-0.0.2-20180822.142647-1.jar"
    }
]
}'''

*/




def allPaths = []

def allJars = []

def allSnaps = []


node('linux'){
	try { 
		cleanWs()

		stage('Call to Artifactory'){

			def call = "https://maven.incomm.com/artifactory/api/search/gavc?g=${groupID}&a=${artifactID}"

			def apiData = sh """curl -X GET ${call} -u ${user}:${pass}"""

			//Converting the data to json obj
			def jsonData = readJSON text: apiData

			//trying to mimic data from artifactory
			//bunch of logic for the POC
			for (i in jsonData.results.uri) {
				def pattern = (i =~  /[^\/]*$/)
				def patterntwo = (i =~ /${groupID}.*/)
				def uri_split = i.split('/')
				allJars.add(pattern[0])
				allPaths.add(patterntwo[0])
				allSnaps.add(uri_split[8])
			}
			echo "Printing the list of jars"
			echo "-------------------------------------------------------------------------------------------"
			println allJars
			echo "-------------------------------------------------------------------------------------------"
			echo "Printing the list of Uri Paths"
			echo "-------------------------------------------------------------------------------------------"
			println allPaths	
			echo "-------------------------------------------------------------------------------------------"
			echo "Printing the list of Snapshots"
			echo "-------------------------------------------------------------------------------------------"
			println allSnaps
			echo "-------------------------------------------------------------------------------------------"
		}

	} catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node

///// The End