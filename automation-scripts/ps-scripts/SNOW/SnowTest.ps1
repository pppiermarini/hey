<#
Authors: SCM, SNOW & AD Team 
Description: Automate SNOW ticket requests for AD related updates
Date: 01/30/2020
Last Modified By: Rohit Kale
#>


#Initialize variables, currently have dummy template setup
#TODO: Update the data fields from SNOW -> Configure Env. vars in Jenkins -> Map values in scripts from SNOW API request

#Mapped to Jenkins UI from field SNOW data, String Parameter Jenkins
$ENVField = $env:ENV 
$HOSTField = $env:HOST
$userIDField = $env:userID
$domainNameField = $env:domainName
$accessServersField = $env:accessServers


#Initialize variables for the payload to send out to SNOW ticket
$devUri = $env:uri

#@TODO: Might be ok to remove
$header = @{
Authorization = $env:Auth

}

#@TODO: Figure out a use case with a SCM specific ticket
$body = @{
 "reqId" = $env:reqId
 "reqNumber" = $env:reqNumber
 "message" = "hallelujah"
 "status" = "Ok"
}

$jsonBody = $body | ConvertTo-Json

# Setting up Auth
$secpasswd = ConvertTo-SecureString $env:sec -AsPlainText -Force
$mycreds = New-Object System.Management.Automation.PSCredential("svc_jenkins", $secpasswd)

# Setting the Allowed Protocols
$AllProtocols = [System.Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'
[System.Net.ServicePointManager]::SecurityProtocol = $AllProtocols
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $True }

#Invoking the POST
Invoke-RestMethod -Uri $devUri -Method 3 -Credential $mycreds -Body $jsonBody -ContentType "application/json" #Make the rest call to update the ticket


#Printing bunch of Vars
Write-Output $("The value of ENVField  is: $ENVField " + "The value of HOSTField is: $HOSTField " + 
"The value of userIDField is: $userIDField " + "The value of domainNameField is: $domainNameField" +
"The value of accessServersField is: $accessServersField" + "The values of header is: $jsonHeader" + "The values of body is: $jsonBody" + "The values of url is: $devUri")