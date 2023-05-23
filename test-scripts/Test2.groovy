import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//pipeline library
@Library('pipeline-shared-library') _

gitRepositoryDepTwo="https://github.com/InComm-Software-Development/mdm-cache-daemon.git"
branchTwo = "${BRANCHDEPTWO}"
gitCreds="scm-incomm"


now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact Two'){
			echo "${gitRepositoryDepTwo} , ${branchTwo}, ${gitCreds}"
				githubCheckout(gitCreds,gitRepositoryDepTwo,branchTwo)

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