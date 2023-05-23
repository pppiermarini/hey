import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

node('linux1') {
    String user = getBuildUserv1()

    stage('Email Distro') {
        def emailDistribution = "jrivett@incomm.com"
        sendEmailv3(emailDistribution, user)
    }
}