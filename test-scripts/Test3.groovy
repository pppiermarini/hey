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

gitRepositoryDepThree="https://github.com/InComm-Software-Development/mdm-swipe-reload.git"
branchThree = "${BRANCHDEPTHREE}"
gitCreds="scm-incomm"
//globals


now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact Three'){
			echo "${gitRepositoryDepThree} , ${branchThree}, ${gitCreds}"
				githubCheckout(gitCreds,gitRepositoryDepThree,branchThree)
				sh """
				ls -ltr
				"""
		}
		
		
	} catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {

		echo 'In Finally!, ie good!'
	}

} //end of node


///// The End