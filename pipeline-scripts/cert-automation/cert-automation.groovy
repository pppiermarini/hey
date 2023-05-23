import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*
import java.util.ArrayList

@Library('pipeline-shared-library') _

certProperties = null

gitRepository="https://github.com/InComm-Software-Development/scm-cert-automation.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

credsName = "scm_deployment"
venafibaseurl="https://api.sslcerts.incomm.com/vedsdk/"
access_token=""
VenafiAccessToken=""
//filebackup="/var/opt/pivotal/pivotal-tc-server/jenkins/conf"

node('master'){
  
		stage('checkout'){
			//cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
			sh "chmod 755 listexpiringcerts.sh"
		}
	
		// Reading data-driven pipeline values from YML file
        stage('Read YAML file') {
            echo 'Reading dataDriven.yml file'
            certProperties = readYaml (file: 'certautomationDataDriven.yml')
	    echo "certProperties${certProperties}"
            if (certProperties == null) {
                throw new Exception("dataDriven.yml not found in the project files.")
            }
	    
	    // TODO: Ensure the values in this sanity check match the values in YML
            echo "Sanity Check"
            if (certProperties == null) {
                throw new Exception("Please fill in the null values: ${certProperties}")
            }
        }
 
   stage ('Venafi Token') {
	   
	if (NewToken == 'true'){
		echo "getting new token"
		 withCredentials([usernamePassword(credentialsId: 'svc_cert_automation', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
		 sh """
			vcert86 getcred --username ${USER} --password ${PASS} -u "$venafibaseurl" --client-id certs-manage --scope "certificate:manage" > NewToken.yml
		   """ 
	projectProperties = readYaml file: "NewToken.yml"
	access_token = "${projectProperties.access_token}"
	echo "access token is ${access_token}"
    }
	}else{
		echo "using current token"
	}


   }
  
   stage ('List expiring certs to renew and download'){
		if (expiring == 'true'){
			withCredentials([string(credentialsId: 'Cert-Decryption', variable: 'SECRET')]) {
				withCredentials([string(credentialsId: 'VenafiAccessToken', variable: 'SECRET2')]) {
				
					sh """
						./listexpiringcerts.sh -t ${SECRET2} --download-password ${SECRET}
					""" 
				}
		}
	}
   }

   stage('Make Backup Directory & Backup Files'){
       sshagent([credsName]) {
	       echo " testing ${certProperties.jks}.${CERT_NAME}.${backupLocation}"
	       echo " ${certProperties.pkcs12}.${CERT_NAME}"
	       echo "${certProperties.jks.certName}"
       //sh """ 
       //ssh -q -o StrictHostKeyChecking=no root@10.42.16.197 'mkdir -p ${filebackup}/backup_april'
       //scp -q -o StrictHostKeyChecking=no root@10.42.16.197 '${certProperties.jks.backupLocation}/catalina.properites backup_april'
	//scp -q -o StrictHostKeyChecking=no root@10.42.16.197 '${certProperties.pkcs12.backupLocation}/catalina.properites backup_april'
       //scp -q -o StrictHostKeyChecking=no root@10.42.16.197 '${certProperties.jks.backupLocation}/jenkins.jks backup_april'
      // """
	}
	
    }

  stage('Get All Certificates') {
// This is an example of how you can process the data
	  if (allCertificates == 'true') {
		echo "Getting all the certs"
		
			
	   sh """
	    curl --request GET --url https://api.sslcerts.incomm.com/vedsdk/Certificates/ --header 'Content-Type:application/json' --header 'Authorization:Bearer ZLdAEt2ikqIP0hF7DrAIvQ==' > allCertificates.json
	   """
		sh "ls -ltr"

		 projectProperties = readJSON file: "allCertificates.json"
		 
		 echo "Certificates"
		 echo "${projectProperties.Certificates}"
		 
		 echo "Certificate Name"
		 echo "${projectProperties.Certificates.Name}"
		 
		 echo "GUID "
		 echo "${projectProperties.Certificates.Guid}"
		 
		 //Read the Name and GUIDs
		 sh """
		 cat allCertificates.json | jq -r '.Certificates[] | {Guid, Name, ValidTo}'
		 """
		 
		 
		sh """
			#TBD  check if any certs are expiriing
		"""
		
		}
  } //Get All Certificates

	}
