import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//Variable configs
myHosts = "${HOSTS}"
myPort = "${PORT}"

// inputs from build with parameters
userInput="${ENVIRONMENT}"

//Splitting each host based on comma
hostNameSplit = myHosts.split(",")


if (userInput == 'spscmbuild02v') {
	node('windows'){
		try { 
		hostNameSplit.each {
		// Running the telnet via PS script argument parameters	
		bat"""
			powershell .\\PSTelnetTest.ps1 $it ${myPort}
		"""
		}	
		//catch any exception 
	} catch(exc) {	throw exc } 

	} //end of node
}

else {
	node('linux'){
		try { 
		hostNameSplit.each {
		// Running the telnet via shell script argument parameters	
		sh"""
			./TelnetTest.sh $it ${myPort}
		"""
		}	
		//catch any exception 
	} catch(exc) {	throw exc } 

	} //end of node


}