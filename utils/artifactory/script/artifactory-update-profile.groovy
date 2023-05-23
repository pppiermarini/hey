import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

@Field profileData
// if you don't skip the admin section is set to false..  probably a bug in Artifactory
skipAdmins = "jdangler, ppiermarini, dstovall, maallen, vhari, skesireddy, svc_jenkins"

pipeline{
	agent {
		label "linux2"
	}
	options {
		disableConcurrentBuilds()
	}

stages{
	stage('Get all the users') {
		
		steps{
			cleanWs()
			script{
	
				withCredentials([usernamePassword(credentialsId: 'ArtifactoryProfiles', passwordVariable: 'ARTPASS', usernameVariable: 'ARTUSER')]) {
					sh """
					curl -u "${ARTUSER}":"${ARTPASS}" --request GET http://maven.incomm.com/artifactory/api/security/users > users.json
					"""
				}
			}
		}

	}

	stage('Read the json') {
		
		steps{
			sleep (5)
			script{
				profileData = readJSON file: "users.json"
				echo "${profileData.name}"
			}
		}
	}

	stage('User Listing') {
		steps{
			echo "######################"
			echo "	User Listing	"
			echo "######################"
			
			script{
				profileData.name.each { println it }
			}
		}
	}

	stage('Update the profiles') {
		when {
			expression { (params.UPDATE_PROFILES == true) }
		}
		
		steps{
			script{
				withCredentials([usernamePassword(credentialsId: 'ArtifactoryProfiles', passwordVariable: 'ARTPASS', usernameVariable: 'ARTUSER')]) {
					profileData.name.each { update(it) }
				}
			}
		}

	}
	
	stage('Update the profiles Dry Run') {
		when {
			expression { (params.UPDATE_PROFILES == false) }
		}
		
		steps{
			script{
				withCredentials([usernamePassword(credentialsId: 'ArtifactoryProfiles', passwordVariable: 'ARTPASS', usernameVariable: 'ARTUSER')]) {
					profileData.name.each { updateDryRun(it) }
				}
			}
		}

	}
}//stages

} //end pipeline

// admins jdangler ppiermarini dstovall maallen vhari skesireddy
def update(user){

	sleep(1)
	if(skipAdmins.contains(user)){
		echo "######################"
		echo "Skipping admin ${user}"
		echo "######################"
	} else {
		
		echo "Updating ${user}"
		
		sh """
		curl -u $ARTUSER:$ARTPASS --request POST http://maven.incomm.com/artifactory/api/security/users/${user} --header 'Content-Type: application/json' --header 'Accept: application/json' --data '{"profileUpdatable": "true"}'
		"""
	}
	
}
def updateDryRun(user){

	sleep(1)
	if(skipAdmins.contains(user)){
		echo "######################"
		echo "Skipping admin ${user}"
		echo "######################"
	} else {
		
		
		
		echo "######################"
		echo "Updating ${user}"
		echo "	Dry Run	"
		echo "######################"

	}
	
}